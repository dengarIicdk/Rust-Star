//! Deterministic, chunk-batched block mutation planning with a hard per-tick
//! budget.
//!
//! Minecraft cannot atomically place two million blocks in one server tick, so
//! this module turns a *request* (a stream of block positions, e.g. a beam)
//! into a *plan*: a list of chunks, each with a sorted set of positions to
//! mutate. The Java side:
//!
//! 1. asks for the plan once (`Plan::build`),
//! 2. applies at most [`MAX_BLOCKS_PER_TICK`] positions per server tick
//!    (`Plan::take_batch`),
//! 3. looks up the owner/rule for each position via the [`BlockSource`]-style
//!    callback the plan was built from — the positions themselves carry no
//!    authority by design.
//!
//! A request larger than [`MAX_BLOCKS_PER_STRIKE`] is *rejected* up front.
//! Because a per-tick budget exists, even the maximum legal request is spread
//! across many ticks and cannot stall the main thread for one frame.

use crate::limits::{MAX_BLOCKS_PER_STRIKE, MAX_BLOCKS_PER_TICK};
use std::collections::HashMap;

/// An integer block position.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub struct BlockPos {
    pub x: i32,
    pub y: i32,
    pub z: i32,
}

/// Produces block positions to mutate. Implemented by [`crate::beam::BeamBlocks`]
/// and, in principle, a `plain` direct-shoot crater or a manual list. The Java
/// side supplies positions; the *planner* decides ordering and batching.
pub trait BlockSource {
    fn next_block(&mut self) -> Option<BlockPos>;
}

/// Key for the chunk map: `(x >> 4, z >> 4)` in world coordinates.
type ChunkKey = (i32, i32);

/// One chunk's worth of pending positions, sorted top-down then by x/z for a
/// stable, aesthetically reasonable excavation order.
#[derive(Debug, Default)]
pub struct ChunkPlan {
    pub cx: i32,
    pub cz: i32,
    pub positions: Vec<BlockPos>,
}

impl ChunkPlan {
    fn push(&mut self, p: BlockPos) {
        self.positions.push(p);
    }

    fn sort(&mut self) {
        // Descending y first (mine from the top), then ascending x, then z.
        self.positions
            .sort_by(|a, b| b.y.cmp(&a.y).then(a.x.cmp(&b.x)).then(a.z.cmp(&b.z)));
    }
}

/// The full plan for one strike: enough to apply it over many ticks.
#[derive(Debug, Default)]
pub struct Plan {
    pub chunks: Vec<ChunkPlan>,
    pub total_blocks: u64,
    next_chunk: usize,
    next_index: usize,
    /// Scratch buffer reused by [`Plan::take_batch`] so a tick's worth of
    /// positions never allocates. Lives with the plan, not the caller.
    batch: Vec<BlockPos>,
}

impl Plan {
    /// Builds a plan from a block source. Returns `Err` if the source yields
    /// more than [`MAX_BLOCKS_PER_STRIKE`] positions — the caller should
    /// *reject* such a request rather than spill it into the world.
    ///
    /// `source` is consumed. Sorting happens per chunk; the chunk iteration
    /// order is deterministic (sorted by key).
    pub fn build(mut source: impl BlockSource) -> Result<Self, &'static str> {
        let mut map: HashMap<ChunkKey, ChunkPlan> = HashMap::new();
        let mut total: u64 = 0;
        while let Some(p) = source.next_block() {
            total += 1;
            if total > u64::from(MAX_BLOCKS_PER_STRIKE) {
                return Err("request exceeds MAX_BLOCKS_PER_STRIKE");
            }
            let key = (p.x.div_euclid(16), p.z.div_euclid(16));
            map.entry(key)
                .or_insert_with(|| ChunkPlan {
                    cx: p.x.div_euclid(16),
                    cz: p.z.div_euclid(16),
                    ..Default::default()
                })
                .push(p);
        }
        let mut chunks: Vec<ChunkPlan> = map.into_values().collect();
        chunks.sort_by_key(|c| (c.cx, c.cz));
        for c in &mut chunks {
            c.sort();
        }
        Ok(Self {
            chunks,
            total_blocks: total,
            next_chunk: 0,
            next_index: 0,
            batch: Vec::new(),
        })
    }

    /// How many chunks this strike touches.
    #[must_use]
    pub fn chunk_count(&self) -> usize {
        self.chunks.len()
    }

    /// True when every position has been handed out.
    #[must_use]
    pub fn is_complete(&self) -> bool {
        self.next_chunk >= self.chunks.len()
    }

    /// Hands out up to `max` positions (the server applies at most
    /// [`MAX_BLOCKS_PER_TICK`] per tick, but callers may pass a smaller value,
    /// e.g. in forced-save frames). Empty when complete.
    ///
    /// Positions are copied into an internal buffer reused across calls, so a
    /// long strike does not allocate once per tick. A `max` above
    /// [`MAX_BLOCKS_PER_TICK`] is clamped: the budget is not advisory.
    pub fn take_batch(&mut self, max: usize) -> &[BlockPos] {
        self.batch.clear();
        if self.is_complete() || max == 0 {
            return &self.batch;
        }
        let budget = max.min(MAX_BLOCKS_PER_TICK as usize);
        while self.batch.len() < budget && !self.is_complete() {
            let chunk = &self.chunks[self.next_chunk];
            let avail = chunk.positions.len() - self.next_index;
            let want = (budget - self.batch.len()).min(avail);
            self.batch
                .extend_from_slice(&chunk.positions[self.next_index..self.next_index + want]);
            self.next_index += want;
            if self.next_index >= chunk.positions.len() {
                self.next_index = 0;
                self.next_chunk += 1;
            }
        }
        &self.batch
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::beam::{BeamBlocks, BeamSpec};

    struct ListSource(Vec<BlockPos>, usize);

    impl BlockSource for ListSource {
        fn next_block(&mut self) -> Option<BlockPos> {
            if self.1 >= self.0.len() {
                None
            } else {
                let p = self.0[self.1];
                self.1 += 1;
                Some(p)
            }
        }
    }

    fn pos(x: i32, y: i32, z: i32) -> BlockPos {
        BlockPos { x, y, z }
    }

    #[test]
    fn plan_from_beam_has_expected_size() {
        let s = BeamSpec {
            center_x: 0.0,
            center_z: 0.0,
            radius: 3.0,
            y_min: 0,
            y_max: 4,
        }
        .validate()
        .expect("valid");
        let mut plan = Plan::build(BeamBlocks::new(s)).expect("under limit");
        assert_eq!(
            plan.total_blocks as usize,
            s.column_footprint_area() as usize * 5
        );
        // Radius 3 around (0,0) spans x,z in -3..=3 → chunks {-1, 0} on both axes.
        assert_eq!(plan.chunk_count(), 4);
        // First handed batch should be the top block of the first sorted column:
        // chunk (-1,-1) sorts first, topmost y first.
        let mut seen = std::collections::HashSet::new();
        let batch = plan.take_batch(8).to_vec();
        assert!(!batch.is_empty());
        assert!(batch.iter().all(|p| p.y == 4));
        for p in batch {
            assert!(seen.insert((p.x, p.y, p.z)), "duplicate in first batch");
        }
        // No batch repeats a position.
        while !plan.is_complete() {
            let b = plan.take_batch(7).to_vec();
            for p in b {
                assert!(seen.insert((p.x, p.y, p.z)), "duplicate");
            }
        }
        assert_eq!(seen.len() as u64, plan.total_blocks);
    }

    #[test]
    fn huge_source_is_rejected() {
        // Feeds MAX_BLOCKS_PER_STRIKE + 1 positions and stops.
        //
        // Still the slowest test, because the guard only trips once the count is
        // exceeded, so it necessarily generates the full limit first. That cost
        // is inherent to testing the real boundary; it is not a runaway loop.
        struct JustOver {
            left: u64,
        }
        impl BlockSource for JustOver {
            fn next_block(&mut self) -> Option<BlockPos> {
                if self.left == 0 {
                    return None;
                }
                self.left -= 1;
                Some(pos(0, 0, 0))
            }
        }
        let over = u64::from(MAX_BLOCKS_PER_STRIKE) + 1;
        assert!(Plan::build(JustOver { left: over }).is_err());
    }

    #[test]
    fn empty_source_is_ok() {
        let p = Plan::build(ListSource(vec![], 0)).expect("ok");
        assert!(p.is_complete());
        assert_eq!(p.total_blocks, 0);
    }

    #[test]
    fn oversized_request_is_clamped_to_the_tick_budget() {
        // The budget is not advisory: asking for more than MAX_BLOCKS_PER_TICK
        // must never hand back more than that, or a caller could stall a tick.
        let s = BeamSpec {
            center_x: 0.0,
            center_z: 0.0,
            radius: 12.0,
            y_min: 0,
            y_max: 200,
        }
        .validate()
        .expect("valid");
        let mut plan = Plan::build(BeamBlocks::new(s)).expect("under cap");
        let batch = plan.take_batch(usize::MAX);
        assert_eq!(batch.len() as u32, MAX_BLOCKS_PER_TICK);
    }

    #[test]
    fn many_small_batches_drain_exactly_once() {
        let s = BeamSpec {
            center_x: 0.0,
            center_z: 0.0,
            radius: 4.0,
            y_min: 0,
            y_max: 12,
        }
        .validate()
        .expect("valid");
        let mut plan = Plan::build(BeamBlocks::new(s)).expect("ok");
        let total = plan.total_blocks;
        let mut seen = std::collections::HashSet::new();
        let mut handed = 0u64;
        while !plan.is_complete() {
            let batch = plan.take_batch(3).to_vec();
            handed += batch.len() as u64;
            for p in batch {
                assert!(seen.insert((p.x, p.y, p.z)), "duplicate position");
            }
        }
        assert_eq!(handed, total);
        assert_eq!(seen.len() as u64, total);
    }

    #[test]
    fn batch_never_exceeds_request() {
        let s = BeamSpec {
            center_x: 0.0,
            center_z: 0.0,
            radius: 1.5,
            y_min: 0,
            y_max: 10,
        }
        .validate()
        .expect("valid");
        let mut plan = Plan::build(BeamBlocks::new(s)).expect("ok");
        let mut total = 0;
        let mut max_seen = 0;
        while !plan.is_complete() {
            let b = plan.take_batch(MAX_BLOCKS_PER_TICK as usize);
            max_seen = max_seen.max(b.len());
            total += b.len();
        }
        assert_eq!(total as u64, plan.total_blocks);
        assert!(max_seen <= MAX_BLOCKS_PER_TICK as usize);
    }
}

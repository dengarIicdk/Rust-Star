// One server tick: hand a bounded batch of blocks to the world for every strike
// in flight. The budget is enforced by the engine, so a huge strike cannot stall
// a tick.
fn starflight_server_tick(game: &mut crate::game::Game<'_>, phase: crate::game::ServerTickPhase) {
    if !matches!(phase, crate::game::ServerTickPhase::End) {
        return;
    }
    crate::game::starflight::apply_tick(game);
}

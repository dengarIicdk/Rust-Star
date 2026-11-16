// Right click with the strike item: claim the click so the item's own use does
// not run, and queue a strike over the block the player stands on.
fn starflight_use_item(
    game: &mut crate::game::Game<'_>,
    stack: &crate::game::ItemStack,
    claimed: &mut bool,
) {
    if *claimed || stack.item != crate::game::starflight::STRIKE_ITEM {
        return;
    }
    let Some((dimension, pos)) = crate::game::starflight::player_place(game) else {
        return;
    };
    let [x, y, z] = pos;
    let center = starflight_engine::Vec3::new(f64::from(x) + 0.5, f64::from(y), f64::from(z) + 0.5);
    match crate::game::starflight::queue(
        dimension,
        center,
        crate::game::starflight::STRIKE_RADIUS,
        y - crate::game::starflight::CARVE_BELOW,
        y + crate::game::starflight::CARVE_ABOVE,
    ) {
        Ok(_) => {
            // The click is ours: the item's vanilla use does not run.
            *claimed = true;
            eprintln!(
                "starflight: strike called at {x} {y} {z} in {dimension}"
            );
        }
        Err(reason) => eprintln!("starflight: strike refused: {reason}"),
    }
}

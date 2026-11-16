// `/starflight strike [radius]`, operator-only: a radius can carve a large
// volume, so it is not something every player may type.
fn starflight_register_commands(
    _game: &mut crate::game::Game<'_>,
    commands: &mut Vec<crate::game::NativeCommand>,
) {
    commands.push(crate::game::starflight::strike_command());
}

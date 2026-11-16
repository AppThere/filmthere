//! FilmCraft's own: a session starts with a project, and the active sequence renders.

use super::*;

#[test]
fn a_new_session_lists_its_commands() {
    let s = loki_session_new();
    let r = call(s, "engine.commands", None);
    assert_eq!(r["ok"], true, "{r}");
    unsafe { loki_session_free(s) };
}

#[test]
fn a_sequence_renders_its_frame() {
    let s = loki_session_new();
    let info = call(s, "loki.surface", None)["result"].clone();
    if info["pages"] == 1 {
        let (mut w, mut h, mut n) = (0, 0, 0);
        // SAFETY: valid session and out-pointers.
        let px = unsafe { loki_render_page(s, 0, 0.25, &mut w, &mut h, &mut n) };
        assert!(!px.is_null() && w > 0 && h > 0 && n == 1, "{info}");
        unsafe { loki_buffer_free(px, w as usize * h as usize * 4) };
    }
    unsafe { loki_session_free(s) };
}

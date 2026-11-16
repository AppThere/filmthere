//! What is FilmCraft's own: the session, how methods reach it, and the Program monitor's frame.

use std::sync::Arc;

use filmcraft_engine::{FsServices, Session};
use filmcraft_render::RenderOptions;
use serde_json::{Value, json};

use crate::Rendered;

pub(crate) struct Inner {
    s: Session,
}

/// A session with the engine's own new project. The person runs the app on their own machine, so
/// files are read and written as the CLI does (`FsServices`).
pub(crate) fn new() -> Inner {
    Inner { s: Session::new(Arc::new(FsServices)) }
}

/// The methods of the control protocol the app uses: `engine.execute {command, params}` and
/// `engine.commands`.
pub(crate) fn call(inner: &mut Inner, method: &str, params: Value) -> Result<Value, String> {
    match method {
        "engine.execute" | "command" => {
            let id = params.get("command").or_else(|| params.get("id")).and_then(Value::as_str).ok_or("`command` required")?;
            let p = params.get("params").cloned().filter(|v| !v.is_null()).unwrap_or_else(|| json!({}));
            let r = inner.s.execute(id, p).map_err(|e| e.to_string());
            // Headless: nothing plays, and the requests for a window or a picker have no one to answer them.
            inner.s.drain_events();
            r
        }
        "engine.commands" => inner.s.execute("command.list", json!({})).map_err(|e| e.to_string()),
        other => Err(format!("unknown method `{other}`")),
    }
}

/// Nothing is queued for the front end yet (the playbook's phase 6).
pub(crate) fn ui_requests(_inner: &mut Inner) -> Value {
    json!([])
}

/// `frame`: the active sequence's picture at the playhead; `width` and `height` are the sequence's.
pub(crate) fn surface(inner: &mut Inner) -> Value {
    match inner.s.active_sequence() {
        Some(seq) => json!({"kind": "frame", "pages": 1, "current": 0, "width": seq.settings.width, "height": seq.settings.height,
            "x": 0, "y": 0}),
        None => json!({"kind": "frame", "pages": 0, "current": 0}),
    }
}

/// The picture of the active sequence at the playhead, at `scale` of the sequence's size, as the
/// Program monitor shows it.
pub(crate) fn render(inner: &mut Inner, page: u32, scale: f32, max_pixels: u64) -> Option<Rendered> {
    if page != 0 {
        return None;
    }
    let seq = inner.s.state.active_sequence?;
    let (w, h) = inner.s.active_sequence().map(|q| (q.settings.width, q.settings.height))?;
    let scale = scale.clamp(0.02, 1.0);
    if (f64::from(w) * f64::from(scale)) * (f64::from(h) * f64::from(scale)) > max_pixels as f64 {
        return None;
    }
    let t = inner.s.playhead();
    let opts = RenderOptions { scale, ..RenderOptions::default() };
    let (pw, ph, rgba) = filmcraft_render::render_sequence_rgba8(&inner.s.project, seq, t, opts, &inner.s.media.provider(inner.s.project.clone(), inner.s.services.clone()));
    if rgba.len() != pw * ph * 4 {
        return None;
    }
    Some(Rendered { width: u32::try_from(pw).ok()?, height: u32::try_from(ph).ok()?, pages: 1, rgba })
}

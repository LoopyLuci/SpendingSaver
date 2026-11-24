//! Theme and small reusable widgets.
use eframe::egui::{self, Color32, RichText, Rounding, Stroke, Ui, Vec2};

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum ThemeMode {
    System,
    Light,
    Dark,
}

impl ThemeMode {
    pub fn from_str(s: &str) -> ThemeMode {
        match s {
            "light" => ThemeMode::Light,
            "dark" => ThemeMode::Dark,
            _ => ThemeMode::System,
        }
    }
    pub fn as_str(self) -> &'static str {
        match self {
            ThemeMode::System => "system",
            ThemeMode::Light => "light",
            ThemeMode::Dark => "dark",
        }
    }
}

pub const ACCENTS: [(&str, Color32); 6] = [
    ("Teal", Color32::from_rgb(0, 150, 136)),
    ("Blue", Color32::from_rgb(33, 150, 243)),
    ("Green", Color32::from_rgb(67, 160, 71)),
    ("Purple", Color32::from_rgb(142, 36, 170)),
    ("Orange", Color32::from_rgb(245, 124, 0)),
    ("Indigo", Color32::from_rgb(63, 81, 181)),
];

pub fn accent(name: &str) -> Color32 {
    ACCENTS.iter().find(|(n, _)| n.eq_ignore_ascii_case(name)).map(|(_, c)| *c).unwrap_or(ACCENTS[0].1)
}

pub const GOOD: Color32 = Color32::from_rgb(46, 160, 90);
pub const BAD: Color32 = Color32::from_rgb(214, 69, 65);

/// Applies the theme once per change, not per frame.
pub fn apply(ctx: &egui::Context, dark: bool, accent: Color32) {
    let mut v = if dark { egui::Visuals::dark() } else { egui::Visuals::light() };
    v.selection.bg_fill = accent;
    v.hyperlink_color = accent;
    v.widgets.hovered.bg_stroke = Stroke::new(1.0_f32, accent);
    v.widgets.active.bg_stroke = Stroke::new(1.0_f32, accent);
    let r = Rounding::same(8.0);
    v.widgets.noninteractive.rounding = r;
    v.widgets.inactive.rounding = r;
    v.widgets.hovered.rounding = r;
    v.widgets.active.rounding = r;
    v.widgets.open.rounding = r;
    v.window_rounding = Rounding::same(12.0);
    v.menu_rounding = Rounding::same(8.0);
    if dark {
        v.panel_fill = Color32::from_rgb(18, 22, 26);
        v.window_fill = Color32::from_rgb(28, 33, 38);
        v.extreme_bg_color = Color32::from_rgb(14, 17, 20);
    } else {
        v.panel_fill = Color32::from_rgb(244, 248, 247);
        v.window_fill = Color32::WHITE;
    }
    ctx.set_visuals(v);
    let mut s = (*ctx.style()).clone();
    s.spacing.item_spacing = Vec2::new(10.0, 8.0);
    s.spacing.button_padding = Vec2::new(12.0, 6.0);
    s.spacing.interact_size.y = 28.0;
    s.animation_time = 0.08;
    ctx.set_style(s);
}

pub fn card<R>(ui: &mut Ui, add: impl FnOnce(&mut Ui) -> R) -> R {
    let fill = ui.visuals().widgets.noninteractive.bg_fill;
    let fill = if ui.visuals().dark_mode { fill.gamma_multiply(1.0) } else { Color32::WHITE };
    egui::Frame::none()
        .fill(fill)
        .rounding(12.0)
        .stroke(Stroke::new(1.0_f32, ui.visuals().widgets.noninteractive.bg_stroke.color))
        .inner_margin(egui::Margin::same(14.0))
        .show(ui, |ui| {
            ui.set_width(ui.available_width());
            add(ui)
        })
        .inner
}

/// The big gradient-free headline figure.
pub fn hero(ui: &mut Ui, accent: Color32, title: &str, value: &str, sub: &str) {
    egui::Frame::none().fill(accent).rounding(16.0).inner_margin(egui::Margin::same(20.0)).show(ui, |ui| {
        ui.set_width(ui.available_width());
        ui.label(RichText::new(title.to_uppercase()).small().color(Color32::from_white_alpha(210)));
        ui.label(RichText::new(value).size(38.0).strong().color(Color32::WHITE));
        ui.label(RichText::new(sub).color(Color32::from_white_alpha(220)));
    });
}

pub fn stat(ui: &mut Ui, label: &str, value: &str, color: Option<Color32>) {
    card(ui, |ui| {
        ui.label(RichText::new(label).small().weak());
        let mut t = RichText::new(value).size(22.0).strong();
        if let Some(c) = color {
            t = t.color(c);
        }
        ui.label(t);
    });
}

pub fn heading(ui: &mut Ui, text: &str) {
    ui.add_space(4.0);
    ui.label(RichText::new(text).size(18.0).strong());
}

/// A dropdown that picks one of `options` (stored value, shown label).
pub fn select(ui: &mut Ui, id: &str, label: &str, current: &mut Option<String>, options: &[(String, String)], none_label: Option<&str>) {
    ui.label(label);
    let shown = current
        .as_ref()
        .and_then(|k| options.iter().find(|(key, _)| key == k).map(|(_, l)| l.clone()))
        .unwrap_or_else(|| none_label.unwrap_or("Select…").to_string());
    egui::ComboBox::from_id_salt(id).width(220.0).selected_text(shown).show_ui(ui, |ui| {
        if let Some(n) = none_label {
            ui.selectable_value(current, None, n);
        }
        for (key, text) in options {
            ui.selectable_value(current, Some(key.clone()), text);
        }
    });
}

/// A text box with a dropdown of suggestions; any text can still be typed.
pub fn combo_edit(ui: &mut Ui, id: &str, value: &mut String, suggestions: &[String], width: f32) {
    ui.horizontal(|ui| {
        ui.add(egui::TextEdit::singleline(value).desired_width(width));
        ui.menu_button("▾", |ui| {
            egui::ScrollArea::vertical().max_height(260.0).id_salt(id).show(ui, |ui| {
                let typed = value.to_lowercase();
                let mut any = false;
                for s in suggestions.iter().filter(|s| typed.is_empty() || s.to_lowercase().contains(&typed) || typed == s.to_lowercase()) {
                    any = true;
                    if ui.button(s).clicked() {
                        *value = s.clone();
                        ui.close_menu();
                    }
                }
                if !any {
                    for s in suggestions {
                        if ui.button(s).clicked() {
                            *value = s.clone();
                            ui.close_menu();
                        }
                    }
                }
            });
        });
    });
}

/// Draws horizontal bars for (label, value) rows.
pub fn bars(ui: &mut Ui, rows: &[(String, f64)], color: Color32, fmt: impl Fn(f64) -> String) {
    let max = rows.iter().map(|r| r.1).fold(0.0_f64, f64::max).max(0.01);
    for (name, v) in rows {
        ui.horizontal(|ui| {
            ui.add_sized([130.0, 20.0], egui::Label::new(name).truncate());
            let w = (ui.available_width() - 110.0).max(40.0);
            let (rect, _) = ui.allocate_exact_size(Vec2::new(w, 14.0), egui::Sense::hover());
            ui.painter().rect_filled(rect, 7.0, ui.visuals().extreme_bg_color);
            let mut fill = rect;
            fill.set_width((rect.width() * (*v / max) as f32).max(3.0));
            ui.painter().rect_filled(fill, 7.0, color);
            ui.label(fmt(*v));
        });
    }
}

pub fn money_color(v: f64) -> Color32 {
    if v < 0.0 { BAD } else { GOOD }
}

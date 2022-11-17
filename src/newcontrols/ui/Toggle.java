package newcontrols.ui;

import arc.func.Boolc;
import arc.func.Func;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.scene.style.Drawable;
import arc.scene.ui.TextButton;
import mindustry.ui.Styles;

public class Toggle extends TextButton {
	/** I don't like this approach too but something's wrong with Button.isChecked as it returns false for no reason. */
	public boolean enabled = false;
	public Func<Toggle, Boolean> toggleProv;
	public Drawable currentStyle = Styles.black;

	/** This might be overly complex for no reason but to look nice */
	public Toggle(String text, boolean enabled, Boolc cons, Drawable hovered, Drawable toggled) {
		super(text, Styles.fullTogglet);
		clicked(() -> {
			setChecked(this.enabled = !this.enabled);
			cons.get(this.enabled);
			currentStyle = toggled;
		});
		hovered(()-> currentStyle = hovered);

		this.setBackground(currentStyle);
		setBackground(currentStyle);
		this.enabled = enabled;
	}

	public Toggle(String text, boolean enabled, Boolc cons){
		this(text, enabled, cons, Styles.grayPanel, Styles.black);
	}

	public Toggle(String text, Func<Toggle, Boolean> enabled, Boolc cons, Drawable hovered, Drawable toggled) {
		this(text, false, cons, hovered, toggled);
		toggle(enabled);
		this.enabled = enabled.get(this);

		clicked(() -> currentStyle = toggled);
		hovered(()-> currentStyle = hovered);

		this.setBackground(currentStyle);
		setBackground(currentStyle);

	}

	public Toggle(String text, Func<Toggle, Boolean> enabled, Boolc cons) {
		this(text, false, cons);
		toggle(enabled);
		this.enabled = enabled.get(this);
	}
	@Override
	public void act(float delta) {
		super.act(delta);

		if (toggleProv != null) {
			boolean state = toggleProv.get(this);
			enabled = state;
			setChecked(state);
		}
	}

	public Toggle toggle(Func<Toggle, Boolean> func) {
		toggleProv = func;
		return this;
	}
}

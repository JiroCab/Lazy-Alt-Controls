package newcontrols.ui;

import arc.func.Boolc;
import arc.scene.style.Drawable;
import arc.scene.ui.Image;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Scl;
import mindustry.ui.Styles;

public class Toggle extends TextButton {
	
	/** I don't like this approach too but something's wrong with Button.isChecked as it returns false for no reason. */
	public boolean enabled = false;
	public Image image;
	protected float padW = 16f, padH = 16f; //padding cus else it looks ugly

	public Toggle(String text, boolean enabled, Boolc cons) {
		super(text, Styles.clearTogglet);
		clicked(() -> {
			setChecked(this.enabled = !this.enabled);
			cons.get(this.enabled);
		});
		
		this.enabled = enabled;
	}
	public Toggle(String text, Boolc cons, Drawable openicon, Drawable closeicon ) {
		super(text, Styles.clearTogglet);
		add(image = new Image(openicon)).size(closeicon.imageSize() * Scl.scl(1f)).padLeft(padW / 2f).left();
		clicked(() -> {
			setChecked(this.enabled = !this.enabled);
			cons.get(this.enabled);
		});
		this.enabled = enabled;
	}

	public Toggle(Drawable openicon, Drawable closeicon, String text, Boolc cons, TextButtonStyle style) {
		super(text, style);
		add(image = new Image(openicon)).size(closeicon.imageSize() * Scl.scl(1f)).padLeft(padW / 2f).left();
		clicked(() -> {
			setChecked(this.enabled = !this.enabled);
			cons.get(this.enabled);
		});
		this.enabled = enabled;
	}

}
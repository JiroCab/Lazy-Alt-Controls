package newcontrols.ui;

import arc.func.Boolc;
import arc.scene.ui.TextButton;
import mindustry.ui.Styles;

public class Toggle extends TextButton {
	
	/** I don't like this approach too but something's wrong with Button.isChecked as it returns false for no reason. */
	public boolean enabled = false;

	public Toggle(String text, boolean enabled, Boolc cons) {
		super(text, Styles.clearTogglet);
		clicked(() -> {
			setChecked(this.enabled = !this.enabled);
			cons.get(this.enabled);
		});
		
		this.enabled = enabled;
	}

}
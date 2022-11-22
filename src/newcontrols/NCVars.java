package newcontrols;

import mindustry.Vars;
import newcontrols.input.AiEnabler;
import newcontrols.ui.fragments.AIPanel;

public class NCVars {
	public static AIPanel aipanel = new AIPanel();
	public static AiEnabler enabler = new AiEnabler();

	public static void init() {
		aipanel.build(Vars.ui.hudGroup);
	}
}

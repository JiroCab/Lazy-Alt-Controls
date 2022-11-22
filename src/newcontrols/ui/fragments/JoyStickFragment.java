package newcontrols.ui.fragments;

import arc.scene.Group;
import newcontrols.NCVars;
import newcontrols.input.AiEnabler;
import newcontrols.ui.Joystick;

/** Moved here to be independent of ActionPanel, Handles joystick rendering*/
public class JoyStickFragment {
    public boolean switchJoySticks = false;

    public void Build(Group parent){

        //default movement joystick
        parent.fill(table -> {
            table.center();
            table.right();

            table.collapser(c -> {
                Joystick common = new Joystick();
                c.add(common).size(200).padRight(2f).padLeft(2f);
                    common.used(pos -> {
                        if (switchJoySticks) {
                        AiEnabler.ai.moveDir.set(pos);
                    }else {
                        AiEnabler.ai.shootDir.set(pos);
                        AiEnabler.ai.shoot = true;
                    }
                });
            }, true, () -> NCVars.enabler.enabled && AiEnabler.ai.manualMode && (switchJoySticks || !AiEnabler.ai.autoAim));
        });

        //default aim & shoot joystick
        parent.fill(table -> {
            table.center();
            table.left();

            table.collapser(c -> {
                ActionPanel.buildPortrait(c, AiEnabler.ai);
                c.row();

                Joystick common = new Joystick();
                c.add(common).size(200).padRight(2f).padLeft(2f);
                common.used(pos -> {
                    if (!switchJoySticks) {
                        AiEnabler.ai.moveDir.set(pos);
                    }else {
                        AiEnabler.ai.shootDir.set(pos);
                        AiEnabler.ai.shoot = true;
                    }
                });
            }, true, () -> NCVars.enabler.enabled && AiEnabler.ai.manualMode && (!switchJoySticks || !AiEnabler.ai.autoAim) );
        });
    }
}

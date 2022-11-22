package newcontrols.input;

import arc.Core;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import mindustry.Vars;
import mindustry.input.InputHandler;
import newcontrols.ui.fragments.JoyStickFragment;

public class AiEnabler {
    /** This exists so hotkeys and different Ui styles can turn on/off the Ai & other components*/
    InputHandler lastHandler = null;
    public static AIInput ai = new AIInput();
    public static JoyStickFragment joyStick = new JoyStickFragment();
    public boolean enabled = false;
    private final Table inputTable = (Table) Core.scene.find("inputTable");
    public static boolean extraLogs  = true;

    public void ToggleAi(){
        enabled =! enabled;

        /*AI handler*/
        if(!enabled){
            if(lastHandler != null){
                ai.finish();
                Vars.control.setInput(lastHandler);
            } else {
                Log.info("Can't Set input to previous Input!");
            }
        } else{
            lastHandler = Vars.control.input;
            Vars.control.setInput(ai);
        }
    }

    /* Joystick Fragment */
    public void ToggleJoystick(){
        if (ai.manualMode && enabled){
            joyStick.Build(Vars.ui.hudGroup);
        }
        /* Rebuild the table */
        if(inputTable != null) {
            inputTable.clear();
            ai.buildPlacementUI(inputTable);
        }else{
            Log.info("InputTable is Null!");
        }

    }
}

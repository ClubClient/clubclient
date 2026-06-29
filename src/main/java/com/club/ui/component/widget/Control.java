package com.club.ui.component.widget;

import com.club.ui.component.Component;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Base for press-to-activate controls (Button / Toggle / Checkbox): the single source of the capture commit-model
 * and keyboard activation, so concrete widgets don't duplicate press/release/keyboard logic (composition mandate).
 *
 * Commit-model (via Container's pressedChild capture): press inside → capture; release inside → activate;
 * release outside → cancel. Keyboard: Space/Enter on the focused control → activate.
 */
abstract class Control extends Component {

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) { pressed = true; return true; }   // consume → Container captures this as pressedChild
        return false;
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        boolean fire = pressed && contains(mx, my);          // release outside bounds cancels
        pressed = false;
        if (fire) activate();
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (enabled && (key == GLFW_KEY_SPACE || key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER)) {
            activate();
            return true;
        }
        return false;
    }

    /** Perform the control's action (run onClick / flip value). Called on release-inside and keyboard activation. */
    protected abstract void activate();
}

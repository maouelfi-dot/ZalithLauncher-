package net.kdt.pojavlaunch.customcontrols;

import android.content.Context;
import android.util.DisplayMetrics;

import net.kdt.pojavlaunch.LwjglGlfwKeycode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads control layouts made with Zalith Launcher 2 ("editorVersion" + "layers") and converts them
 * to the ZL1 / Pojav layout model, so they can be loaded, played and edited here.
 *
 * Converted: normal buttons (key / mouse / scroll / keyboard / menu events, toggle, swipe, pass-through),
 * joysticks (as ZL1 joysticks) and layer visibility (hidden layers are skipped).
 * Not available in ZL1 and therefore skipped: text boxes, layer-switch events, send-text events,
 * custom button styles (default ZL1 colours are used).
 * ZL2 positions are percentages of the free space (screen - widget), sizes are dp or % of a screen side.
 */
public final class Zl2LayoutConverter {
    private Zl2LayoutConverter() {}

    private static final Map<String, Integer> GLFW_CODES = new HashMap<>();

    static {
        for (Field f : LwjglGlfwKeycode.class.getFields()) {
            try {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getName().startsWith("GLFW_")
                        && (f.getType() == short.class || f.getType() == int.class)) {
                    GLFW_CODES.put(f.getName(), ((Number) f.get(null)).intValue());
                }
            } catch (Throwable ignored) { }
        }
    }

    public static boolean isZl2Layout(JSONObject o) {
        return o.has("layers") && o.has("editorVersion");
    }

    public static CustomControls convert(Context ctx, JSONObject root) throws Exception {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        final float density = dm.density;
        final float screenW = Math.max(dm.widthPixels, dm.heightPixels);
        final float screenH = Math.min(dm.widthPixels, dm.heightPixels);

        CustomControls out = new CustomControls();
        out.mControlDataList = new ArrayList<>();
        out.mDrawerDataList = new ArrayList<>();
        out.mJoystickDataList = new ArrayList<>();

        JSONArray layers = root.optJSONArray("layers");
        if (layers == null) return out;
        for (int i = 0; i < layers.length(); i++) {
            JSONObject layer = layers.getJSONObject(i);
            if (layer.optBoolean("hide", false)) continue;

            JSONArray buttons = layer.optJSONArray("normalButtons");
            if (buttons != null) {
                for (int b = 0; b < buttons.length(); b++) {
                    ControlData d = convertButton(buttons.getJSONObject(b), density, screenW, screenH);
                    if (d != null) out.mControlDataList.add(d);
                }
            }
            JSONArray sticks = layer.optJSONArray("joystickButtons");
            if (sticks != null) {
                for (int j = 0; j < sticks.length(); j++) {
                    out.mJoystickDataList.add(convertJoystick(sticks.getJSONObject(j), density, screenW, screenH));
                }
            }
        }
        return out;
    }

    private static String translate(JSONObject text) {
        if (text == null) return "";
        return text.optString("default", "");
    }

    private static String posExpr(String screenVar, String sizeVar, int pos) {
        double p = Math.max(0, Math.min(10000, pos)) / 10000.0;
        return "(${" + screenVar + "} - ${" + sizeVar + "}) * " + p;
    }

    /** @return {widthDp, heightDp} */
    private static float[] sizeDp(JSONObject sz, float density, float screenW, float screenH) {
        String type = sz.optString("type", "percentage");
        if ("dp".equals(type)) {
            return new float[]{(float) sz.optDouble("widthDp", 50), (float) sz.optDouble("heightDp", 50)};
        }
        // percentage (wrap_content is approximated with the default size)
        float w = (float) (sz.optInt("widthPercentage", 1400) / 10000.0)
                * ("screen_width".equals(sz.optString("widthReference")) ? screenW : screenH);
        float h = (float) (sz.optInt("heightPercentage", 1400) / 10000.0)
                * ("screen_width".equals(sz.optString("heightReference")) ? screenW : screenH);
        return new float[]{w / density, h / density};
    }

    private static int mapEvent(JSONObject ev) {
        String type = ev.optString("type");
        String key = ev.optString("key");
        if ("key".equals(type)) {
            switch (key) {
                case "GLFW_MOUSE_BUTTON_LEFT": return ControlData.SPECIALBTN_MOUSEPRI;
                case "GLFW_MOUSE_BUTTON_RIGHT": return ControlData.SPECIALBTN_MOUSESEC;
                case "GLFW_MOUSE_BUTTON_MIDDLE": return ControlData.SPECIALBTN_MOUSEMID;
                default:
                    Integer code = GLFW_CODES.get(key);
                    return code == null ? Integer.MIN_VALUE : code;
            }
        }
        if ("launcher_event".equals(type)) {
            switch (key) {
                case "launcher.event.switch_ime": return ControlData.SPECIALBTN_KEYBOARD;
                case "launcher.event.switch_menu": return ControlData.SPECIALBTN_MENU;
                case "launcher.event.scroll_up":
                case "launcher.event.scroll_up.single": return ControlData.SPECIALBTN_SCROLLUP;
                case "launcher.event.scroll_down":
                case "launcher.event.scroll_down.single": return ControlData.SPECIALBTN_SCROLLDOWN;
                default: return Integer.MIN_VALUE;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static ControlData convertButton(JSONObject n, float density, float screenW, float screenH) {
        float[] size = sizeDp(n.optJSONObject("buttonSize") != null ? n.optJSONObject("buttonSize") : new JSONObject(),
                density, screenW, screenH);
        int[] keys = new int[]{0, 0, 0, 0};
        int count = 0;
        JSONArray events = n.optJSONArray("clickEvents");
        if (events != null) {
            for (int i = 0; i < events.length() && count < 4; i++) {
                int code = mapEvent(events.optJSONObject(i) != null ? events.optJSONObject(i) : new JSONObject());
                if (code != Integer.MIN_VALUE) keys[count++] = code;
            }
        }
        JSONObject pos = n.optJSONObject("position");
        int px = pos != null ? pos.optInt("x", 0) : 0;
        int py = pos != null ? pos.optInt("y", 0) : 0;

        ControlData d = new ControlData(translate(n.optJSONObject("text")), keys);
        d.width = size[0];
        d.height = size[1];
        d.dynamicX = posExpr("screen_width", "width", px);
        d.dynamicY = posExpr("screen_height", "height", py);
        d.isToggle = n.optBoolean("isToggleable", false);
        d.isSwipeable = n.optBoolean("isSwipple", false);
        d.passThruEnabled = n.optBoolean("isPenetrable", false);
        d.displayInGame = true;
        d.displayInMenu = true;
        return d;
    }

    private static ControlJoystickData convertJoystick(JSONObject j, float density, float screenW, float screenH) {
        float size;
        if ("dp".equals(j.optString("sizeType", "percentage"))) {
            size = (float) j.optDouble("sizeDp", 200);
        } else {
            size = (float) (j.optInt("sizePercentage", 2500) / 10000.0) * screenH / density;
        }
        JSONObject pos = j.optJSONObject("position");
        ControlJoystickData d = new ControlJoystickData();
        d.name = "joystick";
        d.width = size;
        d.height = size;
        d.dynamicX = posExpr("screen_width", "width", pos != null ? pos.optInt("x", 0) : 0);
        d.dynamicY = posExpr("screen_height", "height", pos != null ? pos.optInt("y", 10000) : 10000);
        d.forwardLock = j.optBoolean("canLock", true);
        d.absolute = "tap".equalsIgnoreCase(j.optString("triggerMode"));
        d.displayInGame = true;
        d.displayInMenu = false;
        if (d.keycodes == null) d.keycodes = new int[]{0, 0, 0, 0};
        return d;
    }
}

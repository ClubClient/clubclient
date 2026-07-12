package com.club.util;

/**
 * Human key names for the mod's all-ENGLISH UI, derived from the InputUtil translation key
 * ("key.keyboard.left.shift" → "Left Shift"). NOT the game-localized name: on a Russian client
 * vanilla returns Cyrillic caps, which read as random noise next to English labels (owner, Stage 51).
 */
public final class KeyNames {
    private KeyNames() {}

    /** "key.keyboard.left.shift" → "Left Shift"; "key.keyboard.k" → "K"; "key.keyboard.space" → "Space". */
    public static String english(String translationKey) {
        if (translationKey == null) return null;
        String s = translationKey;
        if (s.startsWith("key.keyboard.")) s = s.substring("key.keyboard.".length());
        else if (s.startsWith("key.mouse.")) s = "mouse." + s.substring("key.mouse.".length());
        else if (s.startsWith("key.")) s = s.substring("key.".length());
        StringBuilder sb = new StringBuilder();
        for (String p : s.split("[._]")) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.length() == 0 ? translationKey : sb.toString();
    }
}

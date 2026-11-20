/*
 * Decompiled with CFR 0.152.
 */
package com.familyrelay.local;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OtpDetector {
    public static final String RELAY_PREFIX = "[Family Relay]";
    private static final Pattern CONTEXT = Pattern.compile("\\botp\\b|\\bone[\\s-]*time\\s*(?:password|passcode|code|pin)\\b|\\b(?:verification|authentication|security|confirmation|login|sign[\\s-]*in)\\s+(?:code|pin|password)\\b|\\b(?:verify|authenticate)\\s+(?:using|with)\\b|\\b(?:your|use\\s+(?:the|this))\\s+(?:code|pin)\\b|\\bcode\\s+(?:is|for\\s+(?:your\\s+)?(?:login|sign[\\s-]*in|verification))\\b|\u0913\u091f\u0940\u092a\u0940|\u090f\u0915\\s*\u092c\u093e\u0930\\s*(?:\u0915\u093e\\s*)?\u092a\u093e\u0938\u0935\u0930\u094d\u0921|\u0938\u0924\u094d\u092f\u093e\u092a\u0928\\s*\u0915\u094b\u0921", 66);
    private static final Pattern CODE = Pattern.compile("(?<![\\p{L}\\p{N}])[0-9]{4,8}(?![\\p{L}\\p{N}])");
    private static final Pattern MONEY_BEFORE = Pattern.compile("(?:rs\\.?|inr|usd|eur|gbp|\\$|\\u20b9|\\u20ac|\\u00a3)\\s*$", 2);
    private static final Pattern MONEY_AFTER = Pattern.compile("^\\s*(?:rupees|inr|usd|eur|gbp)\\b", 2);

    private OtpDetector() {
    }

    public static boolean isOtp(String body) {
        if (body == null || body.trim().isEmpty() || body.length() > 4000 || body.trim().startsWith(RELAY_PREFIX)) {
            return false;
        }
        Matcher context = CONTEXT.matcher(body);
        while (context.find()) {
            Matcher candidate = CODE.matcher(body);
            while (candidate.find()) {
                int next;
                int gap = Math.max(0, Math.max(context.start() - candidate.end(), candidate.start() - context.end()));
                if (gap > 90) continue;
                String before = body.substring(Math.max(0, candidate.start() - 15), candidate.start());
                String after = body.substring(candidate.end(), Math.min(body.length(), candidate.end() + 15));
                if (MONEY_BEFORE.matcher(before).find() || MONEY_AFTER.matcher(after).find()) continue;
                int previous = candidate.start() == 0 ? 32 : (int)body.charAt(candidate.start() - 1);
                int n = next = candidate.end() == body.length() ? 32 : (int)body.charAt(candidate.end());
                if ("/.-+=:".indexOf(previous) >= 0 && candidate.start() > 1 && Character.isDigit(body.charAt(candidate.start() - 2)) || "/.-".indexOf(next) >= 0 && candidate.end() + 1 < body.length() && Character.isDigit(body.charAt(candidate.end() + 1)) || previous == 43 || previous == 61 || previous == 47 || previous == 63 || before.matches("(?s).*\\d[ -]$") || after.matches("(?s)^[ -]\\d.*")) continue;
                return true;
            }
        }
        return false;
    }
}


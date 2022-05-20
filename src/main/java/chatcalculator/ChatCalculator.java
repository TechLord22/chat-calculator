package chatcalculator;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nonnull;
import java.text.DecimalFormat;

@Mod(modid = ChatCalculator.MODID,
        name = ChatCalculator.NAME,
        version = ChatCalculator.VERSION)
@Mod.EventBusSubscriber(modid = ChatCalculator.MODID)
public class ChatCalculator {

    public static final String MODID = "chatcalculator";
    public static final String NAME = "Chat Calculator";
    public static final String VERSION = "@VERSION@";

    private static final Style INPUT_STYLE = new Style().setColor(TextFormatting.AQUA);
    private static final Style ANSWER_STYLE = new Style().setColor(TextFormatting.GRAY);
    private static final Style ERROR_STYLE = new Style().setColor(TextFormatting.RED);

    @SubscribeEvent
    public static void onServerChat(@Nonnull ServerChatEvent event) {
        String message = event.getMessage();
        if (!message.startsWith("=")) return;
        if (event.getPlayer() == null) return;

        // don't broadcast the message to everyone
        event.setCanceled(true);

        // display the guide
        if (message.startsWith("=help")) {
            for (int i = 1; i <= 12; i++) {
                event.getPlayer().sendMessage(new TextComponentTranslation(String.format("chatcalculator.help.%s", i),
                        i == 3 ? new TextComponentString("%").setStyle(INPUT_STYLE) : new TextComponentString[]{}));
            }
        } else {
            double result;
            // strip the = sign
            String stripped = message.substring(1);
            // split into expression and args
            String[] split = stripped.split(",");
            // send the input to only the player
            event.getPlayer().sendMessage(new TextComponentString(stripped).setStyle(INPUT_STYLE));
            try {
                result = eval(split[0].toLowerCase(), event.getPlayer());
            } catch (Exception e) {
                // send the error to the player
                event.getPlayer().sendMessage(new TextComponentString(e.getMessage()).setStyle(ERROR_STYLE));
                return;
            }

            // parse arguments
            int decimalPlaces = 3;
            for (int i = 1; i < split.length; i++) {
                String arg = split[i];
                String value = arg.split("=")[1];
                if (arg.startsWith("places")) decimalPlaces = Integer.parseInt(value.replaceAll("\\s", ""));
            }

            // format output
            DecimalFormat formatter = new DecimalFormat("#.###");
            formatter.setMaximumFractionDigits(decimalPlaces);
            String formatted = formatter.format(result);

            // return output
            event.getPlayer().sendMessage(new TextComponentString(formatted).setStyle(ANSWER_STYLE));
        }
    }

    private static void equation(@Nonnull ServerChatEvent event) {

    }

    private static double eval(final String str, final EntityPlayer player) {
        return new Object() {
            private int pos = -1;
            private char ch;

            private void nextChar() {
                ch = (++pos < str.length()) ? str.charAt(pos) : (char) -1;
            }

            private boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) {
                    nextChar();
                    return true;
                }
                return false;
            }

            private double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < str.length())
                    throw new RuntimeException(new TextComponentTranslation("chatcalculator.error.unexpected_char", str.charAt(pos)).getFormattedText());
                return x;
            }

            /**
             * Grammar:
             * expression = term | expression `+` term | expression `-` term
             * term = factor | term `*` factor | term `/` factor | term `%` factor
             * factor = `+` factor | `-` factor | `(` expression `)` | number
             *        | functionName `(` expression `)` | functionName factor
             *        | factor `^` factor
             */
            private double parseExpression() {
                double x = parseTerm();
                for (; ; ) {
                    if (eat('+')) x += parseTerm(); // addition
                    else if (eat('-')) x -= parseTerm(); // subtraction
                    else return x;
                }
            }

            private double parseTerm() {
                double x = parseFactor();
                for (; ; ) {
                    if (eat('*')) x *= parseFactor(); // multiplication
                    else if (eat('/')) x /= parseFactor(); // division
                    else if (eat('%')) x %= parseFactor(); // modulus
                    else return x;
                }
            }

            private double parseFactor() {
                if (eat('+')) return +parseFactor(); // unary plus
                if (eat('-')) return -parseFactor(); // unary minus

                double x;
                int startPos = this.pos;
                if (eat('(')) { // parentheses
                    x = parseExpression();
                    if (!eat(')'))
                        throw new RuntimeException(new TextComponentTranslation("chatcalculator.error.parenthesis_end").getFormattedText());
                } else if ((ch >= '0' && ch <= '9') || ch == '.') { // numbers
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(str.substring(startPos, this.pos));
                } else if (eat('x')) {
                    x = player.getPosition().getX();
                } else if (eat('y')) {
                    x = player.getPosition().getY();
                } else if (eat('z')) {
                    x = player.getPosition().getZ();
                } else if (eat('e')) {
                    x = Math.E;
                } else if (eat('p') && eat('i')) {
                    x = Math.PI;
                } else if (ch >= 'a' && ch <= 'z') { // functions
                    while (ch >= 'a' && ch <= 'z') nextChar();
                    String func = str.substring(startPos, this.pos);
                    if (eat('(')) {
                        x = parseExpression();
                        if (!eat(')'))
                            throw new RuntimeException(new TextComponentTranslation("chatcalculator.error.parenthesis_func", func).getFormattedText());
                    } else {
                        x = parseFactor();
                    }
                    switch (func) {
                        case "sqrt":
                            x = Math.sqrt(x);
                            break;
                        case "log":
                            x = Math.log10(x);
                            break;
                        case "ln":
                            x = Math.log(x);
                            break;
                        case "sin":
                            x = Math.sin(Math.toRadians(x));
                            break;
                        case "cos":
                            x = Math.cos(Math.toRadians(x));
                            break;
                        case "tan":
                            x = Math.tan(Math.toRadians(x));
                            break;
                        case "asin":
                            x = Math.asin(Math.toRadians(x));
                            break;
                        case "acos":
                            x = Math.acos(Math.toRadians(x));
                            break;
                        case "atan":
                            x = Math.atan(Math.toRadians(x));
                            break;
                        default:
                            throw new RuntimeException(new TextComponentTranslation("chatcalculator.error.unknown_func", func).getFormattedText());
                    }
                } else {
                    throw new RuntimeException(new TextComponentTranslation("chatcalculator.error.unexpected_char", str.charAt(str.length() - 1)).getFormattedText());
                }

                if (eat('^')) x = Math.pow(x, parseFactor()); // exponentiation

                return x;
            }
        }.parse();
    }
}

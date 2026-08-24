package com.example.wechataiassistant.service.skill;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 自定义技能：四则运算计算器（确定性输出，不调用 LLM）。
 *
 * <p>触发关键词：计算、算一下、等于多少。</p>
 * <p>支持 + - * / % 和括号，例如「计算 3+5*2」「(3+4)*5 等于多少」。</p>
 */
@Component
public class CalculatorSkill implements Skill {

    @Override
    public String name() {
        return "calculator";
    }

    @Override
    public String description() {
        return "计算四则运算表达式（+ - * / % 和括号）";
    }

    @Override
    public List<String> keywords() {
        return List.of("计算", "算一下", "等于多少");
    }

    @Override
    public String execute(String userId, String content) {
        String expr = content.replaceAll("[\u4e00-\u9fa5a-zA-Z？?！!：:，,、\\s]", "");
        expr = expr.replace('×', '*').replace('÷', '/').replace('X', '*').replace('x', '*');
        if (expr.isEmpty()) {
            return "请把要计算的式子发给我，例如：计算 3+5*2";
        }
        try {
            double result = new Parser(expr).parse();
            String formatted = result == Math.rint(result)
                ? String.valueOf((long) result)
                : String.valueOf(Math.round(result * 1000000.0) / 1000000.0);
            return content + " = " + formatted;
        } catch (IllegalArgumentException e) {
            return "这个算式我没法算：" + e.getMessage() + "（支持 + - * / % 和括号）";
        }
    }

    /** 极简递归下降解析器：表达式 → 项 → 因子。 */
    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        double parse() {
            double v = expression();
            if (pos < s.length()) {
                throw new IllegalArgumentException("无法识别的部分: " + s.substring(pos));
            }
            return v;
        }

        private double expression() {
            double v = term();
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == '+') { pos++; v += term(); }
                else if (c == '-') { pos++; v -= term(); }
                else { break; }
            }
            return v;
        }

        private double term() {
            double v = factor();
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == '*') { pos++; v *= factor(); }
                else if (c == '/') {
                    pos++;
                    double d = factor();
                    if (d == 0) { throw new IllegalArgumentException("除数不能为 0"); }
                    v /= d;
                } else if (c == '%') { pos++; v %= factor(); }
                else { break; }
            }
            return v;
        }

        private double factor() {
            if (pos >= s.length()) { throw new IllegalArgumentException("表达式不完整"); }
            char c = s.charAt(pos);
            if (c == '(') {
                pos++;
                double v = expression();
                if (pos >= s.length() || s.charAt(pos) != ')') {
                    throw new IllegalArgumentException("括号不匹配");
                }
                pos++;
                return v;
            }
            if (c == '-') { pos++; return -factor(); }
            if (Character.isDigit(c) || c == '.') {
                int start = pos;
                while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
                    pos++;
                }
                try {
                    return Double.parseDouble(s.substring(start, pos));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("数字格式错误: " + s.substring(start, pos));
                }
            }
            throw new IllegalArgumentException("非法字符: " + c);
        }
    }
}

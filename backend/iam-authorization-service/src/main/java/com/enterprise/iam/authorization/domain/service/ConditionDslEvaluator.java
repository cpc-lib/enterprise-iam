package com.enterprise.iam.authorization.domain.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * V2.0 Safe Bounded Condition DSL Evaluator (DEC-014).
 *
 * <p>Evaluates non-Turing-complete boolean attribute expressions against a request context map.
 * Enforces strict complexity bounds (max length 1024, max AST/parentheses depth 8, max tokens 64)
 * and fails closed ({@code false}) on any syntax error, bounds violation, missing attribute,
 * or type mismatch.
 */
public final class ConditionDslEvaluator {

    public static final int MAX_EXPRESSION_LENGTH = 1024;
    public static final int MAX_AST_DEPTH = 8;
    public static final int MAX_TOKENS = 64;
    public static final String INVALID_SYNTAX_OR_BOUNDS = "INVALID_DSL_SYNTAX_OR_BOUNDS";

    private ConditionDslEvaluator() {
    }

    public record ValidationResult(
            boolean valid,
            int tokenCount,
            int depth,
            String detail) {
    }

    public record EvaluationResult(
            boolean valid,
            boolean matched,
            int tokenCount,
            int depth,
            String detail) {

        public EvaluationResult(boolean valid, boolean matched, String detail) {
            this(valid, matched, 0, 0, detail);
        }
    }

    public static ValidationResult validate(String expression) {
        ParsedAst parsed = parseInternal(expression);
        return new ValidationResult(parsed.valid, parsed.tokenCount, parsed.depth, parsed.detail);
    }

    public static boolean evaluate(String expression, Map<String, Object> attributes) {
        return evaluateDetailed(expression, attributes).matched();
    }

    public static EvaluationResult evaluateDetailed(String expression, Map<String, Object> attributes) {
        ParsedAst parsed = parseInternal(expression);
        if (!parsed.valid || parsed.root == null) {
            return new EvaluationResult(false, false, parsed.tokenCount, parsed.depth, parsed.detail);
        }
        Map<String, Object> safeAttributes = attributes == null ? Map.of() : attributes;
        try {
            boolean matched = parsed.root.eval(safeAttributes);
            return new EvaluationResult(
                    true,
                    matched,
                    parsed.tokenCount,
                    parsed.depth,
                    matched ? "MATCHED" : "NOT_MATCHED");
        } catch (RuntimeException ex) {
            return new EvaluationResult(true, false, parsed.tokenCount, parsed.depth, "EVALUATION_FAIL_CLOSED");
        }
    }

    private enum TokenType {
        IDENT,
        STRING,
        NUMBER,
        BOOLEAN,
        AND,
        OR,
        NOT,
        EQ,
        NEQ,
        GT,
        GTE,
        LT,
        LTE,
        IN,
        CONTAINS,
        LPAREN,
        RPAREN,
        LBRACKET,
        RBRACKET,
        COMMA
    }

    private record Token(TokenType type, String text) {
    }

    private record ParsedAst(boolean valid, ExprNode root, int tokenCount, int depth, String detail) {
    }

    private static ParsedAst parseInternal(String expression) {
        if (expression == null || expression.isBlank()) {
            return new ParsedAst(false, null, 0, 0, INVALID_SYNTAX_OR_BOUNDS);
        }
        if (expression.length() > MAX_EXPRESSION_LENGTH) {
            return new ParsedAst(false, null, 0, 0, INVALID_SYNTAX_OR_BOUNDS);
        }
        List<Token> tokens = new ArrayList<>();
        int parenDepth = 0;
        int maxParenDepth = 0;
        int i = 0;
        int n = expression.length();
        while (i < n) {
            char c = expression.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (tokens.size() >= MAX_TOKENS) {
                return new ParsedAst(false, null, tokens.size() + 1, maxParenDepth, INVALID_SYNTAX_OR_BOUNDS);
            }
            if (c == '(') {
                parenDepth++;
                if (parenDepth > maxParenDepth) {
                    maxParenDepth = parenDepth;
                }
                if (parenDepth > MAX_AST_DEPTH) {
                    return new ParsedAst(false, null, tokens.size() + 1, parenDepth, INVALID_SYNTAX_OR_BOUNDS);
                }
                tokens.add(new Token(TokenType.LPAREN, "("));
                i++;
            } else if (c == ')') {
                parenDepth--;
                if (parenDepth < 0) {
                    return new ParsedAst(false, null, tokens.size() + 1, maxParenDepth, INVALID_SYNTAX_OR_BOUNDS);
                }
                tokens.add(new Token(TokenType.RPAREN, ")"));
                i++;
            } else if (c == '[') {
                tokens.add(new Token(TokenType.LBRACKET, "["));
                i++;
            } else if (c == ']') {
                tokens.add(new Token(TokenType.RBRACKET, "]"));
                i++;
            } else if (c == ',') {
                tokens.add(new Token(TokenType.COMMA, ","));
                i++;
            } else if (c == '&') {
                if (i + 1 < n && expression.charAt(i + 1) == '&') {
                    tokens.add(new Token(TokenType.AND, "&&"));
                    i += 2;
                } else {
                    return new ParsedAst(false, null, tokens.size() + 1, maxParenDepth, INVALID_SYNTAX_OR_BOUNDS);
                }
            } else if (c == '|') {
                if (i + 1 < n && expression.charAt(i + 1) == '|') {
                    tokens.add(new Token(TokenType.OR, "||"));
                    i += 2;
                } else {
                    return new ParsedAst(false, null, tokens.size() + 1, maxParenDepth, INVALID_SYNTAX_OR_BOUNDS);
                }
            } else if (c == '!') {
                if (i + 1 < n && expression.charAt(i + 1) == '=') {
                    tokens.add(new Token(TokenType.NEQ, "!="));
                    i += 2;
                } else {
                    tokens.add(new Token(TokenType.NOT, "!"));
                    i++;
                }
            } else if (c == '=') {
                if (i + 1 < n && expression.charAt(i + 1) == '=') {
                    tokens.add(new Token(TokenType.EQ, "=="));
                    i += 2;
                } else {
                    return new ParsedAst(false, null, tokens.size() + 1, maxParenDepth, INVALID_SYNTAX_OR_BOUNDS);
                }
            } else if (c == '>') {
                if (i + 1 < n && expression.charAt(i + 1) == '=') {
                    tokens.add(new Token(TokenType.GTE, ">="));
                    i += 2;
                } else {
                    tokens.add(new Token(TokenType.GT, ">"));
                    i++;
                }
            } else if (c == '<') {
                if (i + 1 < n && expression.charAt(i + 1) == '=') {
                    tokens.add(new Token(TokenType.LTE, "<="));
                    i += 2;
                } else {
                    tokens.add(new Token(TokenType.LT, "<"));
                    i++;
                }
            } else if (c == '\'' || c == '"') {
                char quote = c;
                i++;
                StringBuilder sb = new StringBuilder();
                boolean closed = false;
                while (i < n) {
                    char ch = expression.charAt(i);
                    if (ch == '\\' && i + 1 < n) {
                        sb.append(expression.charAt(i + 1));
                        i += 2;
                    } else if (ch == quote) {
                        closed = true;
                        i++;
                        break;
                    } else {
                        sb.append(ch);
                        i++;
                    }
                }
                if (!closed) {
                    return new ParsedAst(false, null, tokens.size() + 1, maxParenDepth, INVALID_SYNTAX_OR_BOUNDS);
                }
                tokens.add(new Token(TokenType.STRING, sb.toString()));
            } else if (Character.isDigit(c) || (c == '-' && i + 1 < n && Character.isDigit(expression.charAt(i + 1)))) {
                int start = i;
                if (c == '-') {
                    i++;
                }
                boolean hasDot = false;
                while (i < n) {
                    char ch = expression.charAt(i);
                    if (Character.isDigit(ch)) {
                        i++;
                    } else if (ch == '.' && !hasDot) {
                        hasDot = true;
                        i++;
                    } else {
                        break;
                    }
                }
                String numStr = expression.substring(start, i);
                if (numStr.endsWith(".")) {
                    return new ParsedAst(false, null, tokens.size() + 1, maxParenDepth, INVALID_SYNTAX_OR_BOUNDS);
                }
                tokens.add(new Token(TokenType.NUMBER, numStr));
            } else if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < n) {
                    char ch = expression.charAt(i);
                    if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '.' || ch == '-') {
                        i++;
                    } else {
                        break;
                    }
                }
                String word = expression.substring(start, i);
                if (word.startsWith(".") || word.endsWith(".") || word.contains("..")) {
                    return new ParsedAst(false, null, tokens.size() + 1, maxParenDepth, INVALID_SYNTAX_OR_BOUNDS);
                }
                if ("AND".equalsIgnoreCase(word)) {
                    tokens.add(new Token(TokenType.AND, word));
                } else if ("OR".equalsIgnoreCase(word)) {
                    tokens.add(new Token(TokenType.OR, word));
                } else if ("NOT".equalsIgnoreCase(word)) {
                    tokens.add(new Token(TokenType.NOT, word));
                } else if ("IN".equalsIgnoreCase(word)) {
                    tokens.add(new Token(TokenType.IN, word));
                } else if ("CONTAINS".equalsIgnoreCase(word)) {
                    tokens.add(new Token(TokenType.CONTAINS, word));
                } else if ("true".equalsIgnoreCase(word) || "false".equalsIgnoreCase(word)) {
                    tokens.add(new Token(TokenType.BOOLEAN, word.toLowerCase()));
                } else {
                    tokens.add(new Token(TokenType.IDENT, word));
                }
            } else {
                return new ParsedAst(false, null, tokens.size() + 1, maxParenDepth, INVALID_SYNTAX_OR_BOUNDS);
            }
        }
        if (parenDepth != 0 || tokens.isEmpty() || tokens.size() > MAX_TOKENS) {
            return new ParsedAst(false, null, tokens.size(), maxParenDepth, INVALID_SYNTAX_OR_BOUNDS);
        }
        Parser parser = new Parser(tokens);
        try {
            ExprNode root = parser.parseOr(1);
            if (!parser.isAtEnd()) {
                return new ParsedAst(false, null, tokens.size(), parser.maxDepth, INVALID_SYNTAX_OR_BOUNDS);
            }
            int effectiveDepth = Math.max(maxParenDepth, parser.maxDepth);
            if (effectiveDepth > MAX_AST_DEPTH) {
                return new ParsedAst(false, null, tokens.size(), effectiveDepth, INVALID_SYNTAX_OR_BOUNDS);
            }
            return new ParsedAst(true, root, tokens.size(), effectiveDepth, "VALID");
        } catch (DslParseException ex) {
            return new ParsedAst(false, null, tokens.size(), Math.max(maxParenDepth, parser.maxDepth), INVALID_SYNTAX_OR_BOUNDS);
        }
    }

    private static final class DslParseException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private interface ExprNode {
        boolean eval(Map<String, Object> attributes);
    }

    private interface ValueOperand {
        Object resolve(Map<String, Object> attributes);
    }

    private record LiteralOperand(Object value) implements ValueOperand {
        @Override
        public Object resolve(Map<String, Object> attributes) {
            return value;
        }
    }

    private record AttributeOperand(String path) implements ValueOperand {
        @Override
        public Object resolve(Map<String, Object> attributes) {
            return resolveAttribute(attributes, path);
        }
    }

    private static final class Parser {
        private final List<Token> tokens;
        private int pos = 0;
        private int maxDepth = 1;

        private Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        private void recordDepth(int depth) {
            if (depth > maxDepth) {
                maxDepth = depth;
            }
            if (depth > MAX_AST_DEPTH) {
                throw new DslParseException();
            }
        }

        private boolean isAtEnd() {
            return pos >= tokens.size();
        }

        private Token peek() {
            return tokens.get(pos);
        }

        private Token advance() {
            return tokens.get(pos++);
        }

        private ExprNode parseOr(int depth) {
            recordDepth(depth);
            ExprNode left = parseAnd(depth);
            while (!isAtEnd() && peek().type() == TokenType.OR) {
                advance();
                recordDepth(depth + 1);
                ExprNode right = parseAnd(depth + 1);
                ExprNode prev = left;
                left = attrs -> prev.eval(attrs) || right.eval(attrs);
            }
            return left;
        }

        private ExprNode parseAnd(int depth) {
            recordDepth(depth);
            ExprNode left = parseUnary(depth);
            while (!isAtEnd() && peek().type() == TokenType.AND) {
                advance();
                recordDepth(depth + 1);
                ExprNode right = parseUnary(depth + 1);
                ExprNode prev = left;
                left = attrs -> prev.eval(attrs) && right.eval(attrs);
            }
            return left;
        }

        private ExprNode parseUnary(int depth) {
            recordDepth(depth);
            if (!isAtEnd() && peek().type() == TokenType.NOT) {
                advance();
                ExprNode inner = parseUnary(depth + 1);
                return attrs -> !inner.eval(attrs);
            }
            if (!isAtEnd() && peek().type() == TokenType.LPAREN) {
                advance();
                ExprNode inner = parseOr(depth + 1);
                if (isAtEnd() || peek().type() != TokenType.RPAREN) {
                    throw new DslParseException();
                }
                advance();
                return inner;
            }
            return parseClause(depth);
        }

        private ExprNode parseClause(int depth) {
            recordDepth(depth);
            if (isAtEnd()) {
                throw new DslParseException();
            }
            ValueOperand left = parseOperand();
            if (isAtEnd()) {
                if (left instanceof LiteralOperand lit && lit.value() instanceof Boolean boolVal) {
                    return attrs -> boolVal;
                }
                if (left instanceof AttributeOperand attrOp) {
                    return attrs -> Boolean.TRUE.equals(attrOp.resolve(attrs));
                }
                throw new DslParseException();
            }
            TokenType nextType = peek().type();
            if (nextType == TokenType.AND || nextType == TokenType.OR || nextType == TokenType.RPAREN) {
                if (left instanceof LiteralOperand lit && lit.value() instanceof Boolean boolVal) {
                    return attrs -> boolVal;
                }
                if (left instanceof AttributeOperand attrOp) {
                    return attrs -> Boolean.TRUE.equals(attrOp.resolve(attrs));
                }
                throw new DslParseException();
            }
            Token op = advance();
            return switch (op.type()) {
                case EQ, NEQ, GT, GTE, LT, LTE, CONTAINS -> {
                    ValueOperand right = parseOperand();
                    yield attrs -> evaluateComparison(op.type(), left.resolve(attrs), right.resolve(attrs));
                }
                case IN -> {
                    List<ValueOperand> elements = parseListOperands();
                    yield attrs -> evaluateIn(left.resolve(attrs), elements, attrs);
                }
                default -> throw new DslParseException();
            };
        }

        private ValueOperand parseOperand() {
            if (isAtEnd()) {
                throw new DslParseException();
            }
            Token token = advance();
            return switch (token.type()) {
                case IDENT -> new AttributeOperand(token.text());
                case STRING -> new LiteralOperand(token.text());
                case NUMBER -> new LiteralOperand(new BigDecimal(token.text()));
                case BOOLEAN -> new LiteralOperand(Boolean.parseBoolean(token.text()));
                default -> throw new DslParseException();
            };
        }

        private List<ValueOperand> parseListOperands() {
            if (isAtEnd() || advance().type() != TokenType.LBRACKET) {
                throw new DslParseException();
            }
            List<ValueOperand> items = new ArrayList<>();
            if (!isAtEnd() && peek().type() == TokenType.RBRACKET) {
                advance();
                return items;
            }
            while (true) {
                items.add(parseOperand());
                if (isAtEnd()) {
                    throw new DslParseException();
                }
                Token sep = advance();
                if (sep.type() == TokenType.RBRACKET) {
                    break;
                }
                if (sep.type() != TokenType.COMMA) {
                    throw new DslParseException();
                }
            }
            return List.copyOf(items);
        }
    }

    private static Object resolveAttribute(Map<String, Object> attributes, String path) {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }
        if (attributes.containsKey(path)) {
            return attributes.get(path);
        }
        String[] parts = path.split("\\.");
        Object current = attributes;
        for (int i = 0; i < parts.length; i++) {
            if (!(current instanceof Map<?, ?> map)) {
                current = null;
                break;
            }
            String part = parts[i];
            if (map.containsKey(part)) {
                current = map.get(part);
            } else {
                current = null;
                break;
            }
        }
        if (current != null) {
            return current;
        }
        // Fallback when attributes is a flat context map and expression uses subject.x / resource.x / context.x / env.x
        if (parts.length == 2
                && ("subject".equals(parts[0])
                        || "resource".equals(parts[0])
                        || "context".equals(parts[0])
                        || "env".equals(parts[0]))) {
            return attributes.get(parts[1]);
        }
        return null;
    }

    private static boolean evaluateIn(Object leftVal, List<ValueOperand> elements, Map<String, Object> attrs) {
        if (leftVal == null || elements.isEmpty()) {
            return false;
        }
        for (ValueOperand operand : elements) {
            Object itemVal = operand.resolve(attrs);
            if (valuesEqual(leftVal, itemVal)) {
                return true;
            }
        }
        return false;
    }

    private static boolean evaluateComparison(TokenType op, Object leftVal, Object rightVal) {
        if (leftVal == null || rightVal == null) {
            return false;
        }
        return switch (op) {
            case EQ -> valuesEqual(leftVal, rightVal);
            case NEQ -> !valuesEqual(leftVal, rightVal);
            case GT -> compareValues(leftVal, rightVal) > 0;
            case GTE -> compareValues(leftVal, rightVal) >= 0;
            case LT -> compareValues(leftVal, rightVal) < 0;
            case LTE -> compareValues(leftVal, rightVal) <= 0;
            case CONTAINS -> evaluateContains(leftVal, rightVal);
            default -> false;
        };
    }

    private static boolean valuesEqual(Object left, Object right) {
        if (left == null || right == null) {
            return false;
        }
        BigDecimal leftNum = toBigDecimalOrNull(left);
        BigDecimal rightNum = toBigDecimalOrNull(right);
        if (leftNum != null && rightNum != null) {
            return leftNum.compareTo(rightNum) == 0;
        }
        if (left instanceof Boolean && right instanceof Boolean) {
            return left.equals(right);
        }
        if (left instanceof String && right instanceof String) {
            return left.equals(right);
        }
        return left.equals(right);
    }

    private static int compareValues(Object left, Object right) {
        BigDecimal leftNum = toBigDecimalOrNull(left);
        BigDecimal rightNum = toBigDecimalOrNull(right);
        if (leftNum != null && rightNum != null) {
            return leftNum.compareTo(rightNum);
        }
        if (left instanceof String leftStr && right instanceof String rightStr) {
            return leftStr.compareTo(rightStr);
        }
        throw new IllegalArgumentException("Incompatible types for comparison");
    }

    private static boolean evaluateContains(Object left, Object right) {
        if (left instanceof Collection<?> col) {
            for (Object item : col) {
                if (valuesEqual(item, right)) {
                    return true;
                }
            }
            return false;
        }
        if (left instanceof String leftStr && right instanceof String rightStr) {
            return leftStr.contains(rightStr);
        }
        return false;
    }

    private static BigDecimal toBigDecimalOrNull(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return null;
    }
}

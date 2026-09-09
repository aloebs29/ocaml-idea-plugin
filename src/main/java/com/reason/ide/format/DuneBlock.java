package com.reason.ide.format;

import com.intellij.formatting.*;
import com.intellij.lang.*;
import com.intellij.psi.*;
import com.intellij.psi.formatter.*;
import com.intellij.psi.formatter.common.*;
import com.intellij.psi.tree.*;
import com.reason.lang.dune.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * A formatting block for one node of a dune file.
 * <p>
 * Dune files are s-expressions, so indentation only depends on how deeply parenthesised a line is: everything
 * inside a pair of parens is indented one step further than the line the opening paren sits on, and the parens
 * themselves are not. That is the style the dune documentation uses:
 * <pre>
 * (package
 *   (name test)
 *   (depends
 *     (ocaml (>= 4.14))
 *     fmt))
 * </pre>
 * <p>
 * Note that this is deliberately an indent-only formatter: {@link #getSpacing} always returns null, so
 * reformatting re-indents lines without ever moving a line break. Dune's own pretty-printer
 * (<code>dune format-dune-file</code>) does reflow, and to a different style — one space, aligned under the
 * paren — so it is not what this reproduces.
 */
public class DuneBlock extends AbstractBlock {
    private static final DuneTypes TYPES = DuneTypes.INSTANCE;
    /** Tokens that open or close a scope: they stay on the enclosing indent instead of being indented into it. */
    private static final TokenSet DELIMITERS = TokenSet.create(TYPES.LPAREN, TYPES.RPAREN, TYPES.VAR_START, TYPES.VAR_END);
    private static final TokenSet CLOSING = TokenSet.create(TYPES.RPAREN, TYPES.VAR_END);

    private final @NotNull Indent myIndent;
    private final int myIndentSize;

    DuneBlock(@NotNull ASTNode node, @NotNull Indent indent, int indentSize) {
        super(node, null, null);
        myIndent = indent;
        myIndentSize = indentSize;
    }

    @Override
    protected List<Block> buildChildren() {
        List<Block> blocks = new ArrayList<>();
        collectChildren(myNode, blocks);
        return blocks;
    }

    private void collectChildren(@NotNull ASTNode parent, @NotNull List<Block> blocks) {
        for (ASTNode child = parent.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getTextRange().isEmpty() || FormatterUtil.containsWhiteSpacesOnly(child)) {
                continue;
            }

            if (child.getElementType() == TYPES.C_FIELDS) {
                // The body of a stanza is not a scope of its own: `(name (a) (b))` parses as
                // `( name |>(a) (b)<| )`, with the fields wrapper starting in the middle of the first line.
                // Its content belongs to the stanza, so splice the children in rather than nesting a block,
                // otherwise every field would be indented twice.
                collectChildren(child, blocks);
            } else {
                blocks.add(new DuneBlock(child, getChildIndent(child), myIndentSize));
            }
        }
    }

    private @NotNull Indent getChildIndent(@NotNull ASTNode child) {
        if (isFile()) {
            return Indent.getNoneIndent(); // stanzas always start at column 0
        }
        return DELIMITERS.contains(child.getElementType()) ? Indent.getNoneIndent() : Indent.getNormalIndent();
    }

    @Override
    public @NotNull Indent getIndent() {
        return myIndent;
    }

    @Override
    public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
        return null; // never constrain whitespace, only indentation
    }

    @Override
    public boolean isLeaf() {
        return getSubBlocks().isEmpty();
    }

    /**
     * Where the caret goes on a line that does not exist yet — which is what pressing enter asks for, and the
     * only thing the formatter can be asked about a file that is still being typed and whose parens are not
     * balanced yet. In that state the unclosed scope has no closing paren, so the fallback below is the
     * indented one.
     */
    @Override
    public @NotNull ChildAttributes getChildAttributes(int newChildIndex) {
        if (isFile()) {
            // A scope left open at the end of the file is closed by the parser just before the trailing
            // whitespace, so the last line of `(package\n  (depends\n` sits outside the stanza and the question
            // lands here rather than on the scope being typed into. The block tree is no help then, but the
            // paren that was never closed is right there in the text, and one step past the indent of its line
            // is what the enclosing block would have answered. It has to be spelled out in spaces because this
            // is relative to the file, which always starts at column 0.
            if (newChildIndex == getSubBlocks().size()) {
                Integer openParenOffset = findInnermostOpenParen();
                if (openParenOffset != null) {
                    return new ChildAttributes(Indent.getSpaceIndent(lineIndentAt(openParenOffset) + myIndentSize), null);
                }
            }
            return new ChildAttributes(Indent.getNoneIndent(), null);
        }

        List<Block> subBlocks = getSubBlocks();
        if (0 < newChildIndex && newChildIndex <= subBlocks.size()) {
            Block previous = subBlocks.get(newChildIndex - 1);
            if (previous instanceof DuneBlock && CLOSING.contains(((DuneBlock) previous).myNode.getElementType())) {
                return new ChildAttributes(Indent.getNoneIndent(), null); // the scope is already closed
            }
        }

        return new ChildAttributes(Indent.getNormalIndent(), null);
    }

    private boolean isFile() {
        return myNode.getPsi() instanceof PsiFile;
    }

    /** Offset of the innermost paren still open at the end of the file, or null if they all match up. */
    private @Nullable Integer findInnermostOpenParen() {
        Deque<Integer> open = new ArrayDeque<>();
        collectOpenParens(myNode, open);
        return open.peek();
    }

    /** Strings and comments are single tokens, so walking the leaves can't mistake a paren inside one for a scope. */
    private static void collectOpenParens(@NotNull ASTNode node, @NotNull Deque<Integer> open) {
        for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
            if (child.getFirstChildNode() != null) {
                collectOpenParens(child, open);
            } else if (child.getElementType() == TYPES.LPAREN) {
                open.push(child.getStartOffset());
            } else if (child.getElementType() == TYPES.RPAREN && !open.isEmpty()) {
                open.pop();
            }
        }
    }

    /** The indentation of the line the given offset is on, in columns. */
    private int lineIndentAt(int offset) {
        CharSequence text = myNode.getChars();
        int lineStart = 0;
        for (int i = offset - 1; 0 <= i; i--) {
            if (text.charAt(i) == '\n') {
                lineStart = i + 1;
                break;
            }
        }

        int indent = 0;
        while (lineStart + indent < offset && text.charAt(lineStart + indent) == ' ') {
            indent++;
        }
        return indent;
    }
}

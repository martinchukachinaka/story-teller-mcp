package com.cc.whatif.storytellermcp.infrastructure.exceptions;

/**
 * Signals an expected, recoverable rejection of a story operation (story not found,
 * already complete, invalid chapter data, etc.) — as opposed to a genuine bug.
 * Callers at the MCP boundary catch this and translate it into a rejected response
 * rather than letting it surface as a protocol-level error.
 */
public class StoryException extends RuntimeException {
    public StoryException(String message) {
        super(message);
    }
}

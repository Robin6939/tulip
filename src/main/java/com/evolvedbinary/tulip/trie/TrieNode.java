package com.evolvedbinary.tulip.trie;

import com.evolvedbinary.tulip.lexer.TokenType;

import java.util.HashMap;
import java.util.Map;

public class TrieNode {
    Map<Byte, TrieNode> children = new HashMap<>();
    public boolean isKeyword = false;
    public TokenType tokenType = null;
}
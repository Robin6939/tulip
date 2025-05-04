package com.evolvedbinary.tulip.trie;

import com.evolvedbinary.tulip.lexer.TokenType;

public class Trie {
    private final TrieNode root = new TrieNode();


    public void insert(String word, TokenType tokenType) {
        TrieNode node = root;
        for (byte b : word.getBytes()) { // Convert to byte array
            node.children.putIfAbsent(b, new TrieNode());
            node = node.children.get(b);
        }
        node.isKeyword = true;
        node.tokenType = tokenType;
    }

    public TrieNode traverse(byte b, TrieNode node) {
        return node.children.get(b);
    }

    public TrieNode getRoot() {
        return root;
    }
}
/*
 *  Copyright (C) 2010-2023 JPEXS, All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.jpexs.decompiler.flash.importers.svg.css;

import com.jpexs.helpers.Reference;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * CSS Stylesheet parser. Based on https://www.w3.org/TR/CSS21/grammar.html
 *
 * @author JPEXS
 */
public class CssParser {

    private final CssLexer lexer;
    private final String s;

    private final List<String> selectors = new ArrayList<>();
    private final List<String> declarations = new ArrayList<>();

    private final List<List<String>> propNames = new ArrayList<>();
    private final List<List<String>> propValues = new ArrayList<>();

    private final List<Integer> specifities = new ArrayList<>();

    public CssParser(String s) {
        this.s = s;
        this.lexer = new CssLexer(new StringReader(s));
    }

    public void styleshet() throws IOException, CssParseException {
        CssParsedSymbol symb = lex();
        if (symb.type == CssSymbolType.CHARSET_SYM) {
            expect(CssSymbolType.STRING);
            expect(";");
            symb = lex();
        }
        while (symb.isType(CssSymbolType.S, CssSymbolType.CDO, CssSymbolType.CDC)) {
            symb = lex();
        }
        while (symb.type == CssSymbolType.IMPORT_SYM) {
            sstar();
            expect(CssSymbolType.STRING, CssSymbolType.URI);
            sstar();
            symb = lex();
            if (!symb.isType(";")) {
                lexer.pushback(symb);
                media_list();
                expect(";");
            }
            sstar();
            symb = lex();
            while (symb.isType(CssSymbolType.CDO, CssSymbolType.CDC)) {
                sstar();
                symb = lex();
            }
        }

        while (symb.type != CssSymbolType.EOF) {
            if (symb.type == CssSymbolType.MEDIA_SYM) {
                lexer.pushback(symb);
                media();
            } else if (symb.type == CssSymbolType.PAGE_SYM) {
                lexer.pushback(symb);
                List<String> propNames = new ArrayList<>();
                List<String> propValues = new ArrayList<>();
                page(propNames, propValues);
            } else {
                lexer.pushback(symb);
                if (!ruleset()) {
                    break;
                }
            }
            symb = lex();
            while (symb.isType(CssSymbolType.CDO, CssSymbolType.CDC)) {
                sstar();
                symb = lex();
            }
        }
    }

    private boolean ruleset() throws IOException, CssParseException {
        int posSelectorStart = lexer.getPos();
        int specifity = selector();
        if (specifity == -1) {
            return false;
        }
        CssParsedSymbol symb = lex();
        while (symb.isType(",")) {
            sstar();
            specifity += selector();
            symb = lex();
        }
        expect(symb, "{");

        int posSelectorEnd = lexer.getPos() - 1;
        String selectorStr = s.substring(posSelectorStart, posSelectorEnd).trim();
        selectors.add(selectorStr);
        specifities.add(specifity);

        int declarationsStart = lexer.getPos();
        Reference<String> propName = new Reference<>("");
        Reference<String> propValue = new Reference<>("");

        List<String> propNames = new ArrayList<>();
        List<String> propValues = new ArrayList<>();

        sstar();
        symb = lex();
        if (symb.type == CssSymbolType.IDENT) {
            lexer.pushback(symb);
            declaration(propName, propValue);
            propNames.add(propName.getVal());
            propValues.add(propValue.getVal());
            symb = lex();
        }
        while (symb.isType(";")) {
            sstar();
            symb = lex();
            if (symb.type == CssSymbolType.IDENT) {
                lexer.pushback(symb);
                declaration(propName, propValue);
                propNames.add(propName.getVal());
                propValues.add(propValue.getVal());
                symb = lex();
            }
        }
        expect(symb, "}");
        int declarationsEnd = lexer.getPos() - 1;
        String declaration = s.substring(declarationsStart, declarationsEnd);
        declarations.add(declaration);

        this.propNames.add(propNames);
        this.propValues.add(propValues);

        sstar();
        return true;
    }

    private int selector() throws IOException, CssParseException {
        int specifity = simple_selector();
        if (specifity == -1) {
            return -1;
        }

        CssParsedSymbol symb = lex();
        if (symb.type == CssSymbolType.S) {
            while (symb.type == CssSymbolType.S) {
                symb = lex();
            }
            if (symb.isType("+", ">")) {
                sstar();
                specifity += selector();
            } else {
                lexer.pushback(symb);
                if (symb.isType("*", ".", "[", ":") || symb.isType(CssSymbolType.IDENT, CssSymbolType.HASH)) {
                    specifity += selector();
                }
            }
        } else if (symb.isType("+", ">")) {
            sstar();
            specifity += selector();
        } else {
            lexer.pushback(symb);
        }
        return specifity;
    }

    private int simple_selector() throws IOException, CssParseException {
        CssParsedSymbol symb = lex();
        int specifity = 0;
        if (symb.isType(CssSymbolType.IDENT) || symb.isType("*")) {
            if (symb.isType(CssSymbolType.IDENT)) {
                specifity += 1;
            }
            while (true) {
                symb = lex();
                if (symb.type == CssSymbolType.HASH) {
                    specifity += 100;
                } else if (symb.isType(".")) {
                    expect(CssSymbolType.IDENT);
                    specifity += 10;
                } else if (symb.isType("[")) {
                    lexer.pushback(symb);
                    attrib();
                    specifity += 10;
                } else if (symb.isType(":")) {
                    lexer.pushback(symb);
                    pseudo();
                    specifity += 10;
                } else {
                    lexer.pushback(symb);
                    break;
                }
            }
        } else {
            int count = 0;
            while (true) {
                if (symb.type == CssSymbolType.HASH) {
                    specifity += 100;
                    count++;
                } else if (symb.isType(".")) {
                    expect(CssSymbolType.IDENT);
                    specifity += 10;
                    count++;
                } else if (symb.isType("[")) {
                    lexer.pushback(symb);
                    attrib();
                    specifity += 10;
                    count++;
                } else if (symb.isType(":")) {
                    lexer.pushback(symb);
                    pseudo();
                    specifity += 10;
                    count++;
                } else {
                    if (count == 0) {
                        lexer.pushback(symb);
                        return -1;
                    }
                    break;
                }
                symb = lex();
            }
            lexer.pushback(symb);
        }
        return specifity;
    }

    private void pseudo() throws IOException, CssParseException {
        expect(":");
        CssParsedSymbol symb = lex();
        if (symb.type == CssSymbolType.IDENT) {
            //okay
        } else if (symb.type == CssSymbolType.FUNCTION) {
            sstar();
            symb = lex();
            if (symb.type == CssSymbolType.IDENT) {
                sstar();
                symb = lex();
            }
            expect(symb, ")");
        }
    }

    private void attrib() throws IOException, CssParseException {
        expect("[");
        sstar();
        expect(CssSymbolType.IDENT);
        sstar();
        CssParsedSymbol symb = lex();
        if (symb.isType(CssSymbolType.INCLUDES, CssSymbolType.DASHMATCH) || symb.isType("=")) {
            sstar();
            expect(CssSymbolType.IDENT, CssSymbolType.STRING);
            sstar();
            symb = lex();
        }
        expect(symb, "]");

    }

    private void page(List<String> propNames, List<String> propValues) throws IOException, CssParseException {
        expect(CssSymbolType.PAGE_SYM);
        sstar();
        CssParsedSymbol symb = lex();
        if (symb.isType(":")) { //pseudo_page
            expect(CssSymbolType.IDENT);
            sstar();
            symb = lex();
        }
        expect(symb, "{");
        sstar();
        Reference<String> propName = new Reference<>("");
        Reference<String> propValue = new Reference<>("");
        declaration(propName, propValue);
        propNames.add(propName.getVal());
        propValues.add(propValue.getVal());
        symb = lex();
        while (symb.isType(";")) {
            sstar();
            symb = lex();
            if (!symb.isType(";", "}")) {
                declaration(propName, propValue);
                propNames.add(propName.getVal());
                propValues.add(propValue.getVal());
            }
        }
        expect(symb, "}");

    }

    private void declaration(Reference<String> propName, Reference<String> propValue) throws IOException, CssParseException {
        CssParsedSymbol symb = lex();
        expect(symb, CssSymbolType.IDENT);
        propName.setVal(symb.value);
        sstar();
        expect(":");
        sstar();
        lexer.startBuffer();
        expr();
        symb = lex();
        if (symb.type == CssSymbolType.IMPORTANT_SYM) {
            sstar();
        } else {
            lexer.pushback(symb);
        }
        propValue.setVal(lexer.getAndClearBuffer());
    }

    private void expr() throws IOException, CssParseException {
        term(true);
        while (true) {
            CssParsedSymbol symb = lex();
            if (symb.isType("/", ",")) {
                sstar();
                term(true);
            } else {
                lexer.pushback(symb);
                if (!term(false)) {
                    break;
                }
            }
        }

    }

    private boolean term(boolean required) throws IOException, CssParseException {
        CssParsedSymbol symb = lex();
        if (symb.isType("-", "+")) {
            symb = lex();
        }
        if (symb.isType(CssSymbolType.NUMBER, CssSymbolType.PERCENTAGE,
                CssSymbolType.LENGTH, CssSymbolType.EMS, CssSymbolType.EXS,
                CssSymbolType.ANGLE, CssSymbolType.TIME, CssSymbolType.FREQ)) {
            sstar();
        } else if (symb.isType(CssSymbolType.STRING, CssSymbolType.IDENT, CssSymbolType.URI)) {
            sstar();
        } else if (symb.type == CssSymbolType.HASH) {
            sstar();
        } else if (symb.type == CssSymbolType.FUNCTION) {
            sstar();
            expr();
            expect(")");
            sstar();
        } else {
            lexer.pushback(symb);
            if (required) {
                throw new CssParseException();
            }
            return false;
        }
        return true;
    }


    private void media() throws IOException, CssParseException {
        expect(CssSymbolType.MEDIA_SYM);
        sstar();
        media_list();
        expect("{");
        sstar();
        CssParsedSymbol symb = lex();
        while (!symb.isType("}")) {
            if (!ruleset()) {
                break;
            }
            symb = lex();
        }
        sstar();
    }

    private void medium() throws IOException, CssParseException {
        expect(CssSymbolType.IDENT);
        sstar();
    }

    private void media_list() throws IOException, CssParseException {
        medium();
        CssParsedSymbol symb = lex();
        while (symb.isType(",")) {
            sstar();
            medium();
        }
        lexer.pushback(symb);
    }

    private void sstar() throws IOException {
        CssParsedSymbol symb = lex();
        while (symb.type == CssSymbolType.S) {
            symb = lex();
        }
        lexer.pushback(symb);
    }

    private CssParsedSymbol lex() throws IOException {
        CssParsedSymbol v = lexer.lex();
        //System.err.println("" + v);
        return v;
    }

    private void expect(String s) throws IOException, CssParseException {
        CssParsedSymbol symb = lex();
        if (symb.type != CssSymbolType.OTHER) {
            throw new CssParseException(s + " expected but " + symb + " found");
        }
        if (!s.equals(symb.value)) {
            throw new CssParseException(s + " expected but " + symb + " found");
        }
    }

    private void expect(CssParsedSymbol symb, String s) throws IOException, CssParseException {
        if (symb.type != CssSymbolType.OTHER) {
            throw new CssParseException(s + " expected but " + symb + " found");
        }
        if (!s.equals(symb.value)) {
            throw new CssParseException(s + " expected but " + symb.value + " found");
        }
    }

    private void expect(CssParsedSymbol symb, CssSymbolType... types) throws IOException, CssParseException {
        List<String> toPrint = new ArrayList<>();
        for (CssSymbolType type : types) {
            toPrint.add(type.toString());
            if (symb.type == type) {
                return;
            }
        }

        throw new CssParseException(String.join(",", toPrint) + " expected but " + symb + " found");
    }

    private void expect(CssSymbolType... types) throws IOException, CssParseException {
        List<String> toPrint = new ArrayList<>();
        CssParsedSymbol symb = lex();

        for (CssSymbolType type : types) {
            toPrint.add(type.toString());
            if (symb.type == type) {
                return;
            }
        }

        throw new CssParseException(String.join(",", toPrint) + " expected but " + symb + " found");
    }

    public int getCountRulesets() {
        return declarations.size();
    }

    public String getSelector(int index) {
        return selectors.get(index);
    }

    public String getDeclarations(int index) {
        return declarations.get(index);
    }

    public int getPropertyCount(int index) {
        return propNames.get(index).size();
    }

    public String getPropertyName(int ruleSetIndex, int propertyIndex) {
        return propNames.get(ruleSetIndex).get(propertyIndex);
    }

    public String getPropertyValue(int ruleSetIndex, int propertyIndex) {
        return propValues.get(ruleSetIndex).get(propertyIndex);
    }

    public int getSpecifity(int index) {
        return specifities.get(index);
    }
}

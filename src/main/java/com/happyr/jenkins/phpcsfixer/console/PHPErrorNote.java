/*
 * The MIT License
 * 
 * Copyright (c) 2008-2011, Jenkins project, Seiji Sogabe
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.happyr.jenkins.phpcsfixer.console;

import hudson.Extension;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;

/**
 * Annotates the PHP error line of the Phing execution.
 *
 * @author Seiji Sogabe
 */
public class PHPErrorNote extends ConsoleNote {


    @Override
    public ConsoleAnnotator<?> annotate(Object context, MarkupText text, int charPos) {
        if (!ENABLED) {
            return null;
        }

        if (text.getText().contains("Notice")) {
            text.addMarkup(0, text.length(), "<span class='phing-phperror-notice'>", "</span>");
            return null;
        }

        if (text.getText().contains("Warning error")) {
            text.addMarkup(0, text.length(), "<span class='phing-phperror-warning'>", "</span>");
            return null;
        }

        if (text.getText().contains("Parse error")) {
            text.addMarkup(0, text.length(), "<span class='phing-phperror-parse'>", "</span>");
            return null;
        }

        if (text.getText().contains("Fatal error")) {
            text.addMarkup(0, text.length(), "<span class='phing-phperror-fatal'>", "</span>");
            return null;
        }

        return null;
    }

    @Extension
    public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {

        public String getDisplayName() {
            return "PHP Error Note";
        }
    }

    private static final boolean ENABLED = !Boolean.getBoolean(PHPErrorNote.class.getName() + ".disabled");
}

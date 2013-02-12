/*
 * Copyright 2008 Outerthought bvba and Schaubroeck nv
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kauriproject.template.taglib;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.kauriproject.template.*;
import org.kauriproject.template.el.ELFacade;
import org.kauriproject.template.el.Expression;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;

/**
 * Implementation of a "test" template instruction.
 */
public class TestBlock extends TemplateBlock {

    private static final String TEXT = "supporting taglibs from the start";

    protected static Log log = LogFactory.getLog(TestBlock.class);

    // test CONSTANTS
    public static final String TEST = "test";

    // EL
    protected ELFacade elFacade;

    // special
    protected TextStep textStep;

    public TestBlock() {
        // default constructor does nothing
        super(null);
    }

    public TestBlock(ELFacade elFacade, Locator locator, SaxElement saxElement) {
        super(saxElement);
        this.elFacade = elFacade;
        this.textStep = new TextStep(elFacade, locator, TEXT.toCharArray(), 0, TEXT.length());
    }

    protected Step buildStartStep(Locator locator, NamespacesHandle namespacesHandle) {
        return new StartStep(locator);
    }

    protected Step buildEndStep(Locator locator) {
        return new EndStep(locator);
    }

    class StartStep extends Step {

        private Expression testExpr;

        // compiledNext: first nested instruction
        // runtimeNext may be newly composed step

        public StartStep( Locator locator) {
            super(locator);
            Attributes attributes = getSaxElement().getAttributes();

            String testAttr = attributes.getValue(TEST);
            testExpr = elFacade.createExpression(testAttr, Boolean.class);
        }

        @Override
        public Step executeAndProceed(ExecutionContext context, TemplateResult result) {

            final boolean test = (Boolean) testExpr.evaluate(context.getTemplateContext());

            if (test) {
                return getCompiledNext();
            } else {
                textStep.setCompiledNext(endStep);
                return textStep;
            }

        }
    }

    class EndStep extends Step {

        // compiledNext: first following instruction after block
        // runtimeNext idem

        public EndStep(Locator locator) {
            super(locator);
        }

    }

}

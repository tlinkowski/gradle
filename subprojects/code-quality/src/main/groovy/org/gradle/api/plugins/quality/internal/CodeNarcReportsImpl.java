/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.plugins.quality.internal;

import org.gradle.api.Task;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.plugins.quality.CodeNarcReports;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.api.reporting.internal.TaskGeneratedSingleFileReport;
import org.gradle.api.reporting.internal.TaskReportContainer;
import org.gradle.util.DeprecationLogger;

import javax.inject.Inject;

public class CodeNarcReportsImpl extends TaskReportContainer<SingleFileReport> implements CodeNarcReports {

    /**
     * This internal constructor is used by the 'nebula.lint' plugin which we test as part of our ci pipeline.
     * */
    @Deprecated
    public CodeNarcReportsImpl(Task task) {
        this(task, CollectionCallbackActionDecorator.NOOP);
        DeprecationLogger.nagUserOfDeprecated("Internal API constructor CodeNarcReportsImpl(Task)", "Don't ex");
    }

    @Inject
    public CodeNarcReportsImpl(Task task, CollectionCallbackActionDecorator callbackActionDecorator) {
        super(SingleFileReport.class, task, callbackActionDecorator);

        add(TaskGeneratedSingleFileReport.class, "xml", task);
        add(TaskGeneratedSingleFileReport.class, "html", task);
        add(TaskGeneratedSingleFileReport.class, "text", task);
        add(TaskGeneratedSingleFileReport.class, "console", task);
    }

    public SingleFileReport getXml() {
        return getByName("xml");
    }

    public SingleFileReport getHtml() {
        return getByName("html");
    }

    public SingleFileReport getText() {
        return getByName("text");
    }
}

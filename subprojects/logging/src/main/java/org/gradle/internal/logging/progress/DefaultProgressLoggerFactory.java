/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.logging.progress;

import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.time.Clock;
import org.gradle.util.GUtil;

import javax.annotation.Nullable;

public class DefaultProgressLoggerFactory implements ProgressLoggerFactory {
    private final ProgressListener progressListener;
    private final Clock clock;
    private final BuildOperationIdFactory buildOperationIdFactory;
    private final ThreadLocal<ProgressLoggerImpl> current = new ThreadLocal<ProgressLoggerImpl>();
    private final CurrentBuildOperationRef currentBuildOperationRef = CurrentBuildOperationRef.instance();
    private final ThreadLocal<BuildOperationDescriptor> lastLoggedBuildOperation = new ThreadLocal<BuildOperationDescriptor>();

    public DefaultProgressLoggerFactory(ProgressListener progressListener, Clock clock, BuildOperationIdFactory buildOperationIdFactory) {
        this.progressListener = progressListener;
        this.clock = clock;
        this.buildOperationIdFactory = buildOperationIdFactory;
    }

    @Override
    public ProgressLogger newOperation(Class loggerCategory) {
        return newOperation(loggerCategory.getName());
    }

    @Override
    public ProgressLogger newOperation(Class loggerCategory, BuildOperationDescriptor descriptor) {
        if (isNotInterestingForProgressListeners(descriptor)) {
            return new NoopProgressLogger();
        }

        String category = ProgressStartEvent.BUILD_OP_CATEGORY;
        if (descriptor.getOperationType() == BuildOperationCategory.TASK) {
            // This is a legacy quirk.
            // Scans use this to determine that progress logging is indicating start/finish of tasks.
            // This can be removed in Gradle 5.0 (along with the concept of a “logging category” of an operation)
            category = ProgressStartEvent.TASK_CATEGORY;
        }


        ProgressLoggerImpl logger = new ProgressLoggerImpl(
            null,
            descriptor.getId(),
            category,
            progressListener,
            clock,
            true,
            descriptor.getId(),
            descriptor.getParentId(),
            descriptor.getOperationType()
        );
        logger.totalProgress = descriptor.getTotalProgress();

        // Make some assumptions about the console output
        if (descriptor.getOperationType().isTopLevelWorkItem()) {
            logger.setLoggingHeader(descriptor.getProgressDisplayName());
        }

        lastLoggedBuildOperation.set(descriptor);

        return logger;
    }

    private boolean isNotInterestingForProgressListeners(BuildOperationDescriptor descriptor) {
        return descriptor.getProgressDisplayName() == null && descriptor.getOperationType() == BuildOperationCategory.UNCATEGORIZED && descriptor.getParentId() != null;
    }

    public ProgressLogger newOperation(String loggerCategory) {
        return init(loggerCategory, null);
    }

    public ProgressLogger newOperation(Class loggerClass, ProgressLogger parent) {
        return init(loggerClass.toString(), parent);
    }

    private ProgressLogger init(
        String loggerCategory,
        @Nullable ProgressLogger parentOperation
    ) {
        if (parentOperation != null && !(parentOperation instanceof ProgressLoggerImpl)) {
            throw new IllegalArgumentException("Unexpected parent logger.");
        }

        OperationIdentifier buildOperationId = null;
        OperationIdentifier parentBuildOperationId = null;
        BuildOperationDescriptor lastLoggedBuildOperation = this.lastLoggedBuildOperation.get();
        if (lastLoggedBuildOperation != null) {
            buildOperationId = lastLoggedBuildOperation.getId();
            parentBuildOperationId = lastLoggedBuildOperation.getParentId();
        } else {
            BuildOperationRef currentBuildOperation = currentBuildOperationRef.get();
            if (currentBuildOperation != null) {
                buildOperationId = currentBuildOperation.getId();
                parentBuildOperationId = currentBuildOperation.getParentId();
            }
        }

        return new ProgressLoggerImpl(
            (ProgressLoggerImpl) parentOperation,
            new OperationIdentifier(buildOperationIdFactory.nextId()),
            loggerCategory,
            progressListener,
            clock,
            false,
            buildOperationId,
            parentBuildOperationId,
            null
        );
    }

    private enum State {idle, started, completed}

    private class ProgressLoggerImpl implements ProgressLogger {
        private final OperationIdentifier progressOperationId;
        private final String category;
        private final ProgressListener listener;
        private final Clock clock;
        private final boolean buildOperationStart;
        @Nullable
        private final OperationIdentifier buildOperationId;
        @Nullable
        private final OperationIdentifier parentBuildOperationId;
        private final BuildOperationCategory buildOperationCategory;
        private ProgressLoggerImpl previous;
        private ProgressLoggerImpl parent;
        private String description;
        private String loggingHeader;
        private State state = State.idle;
        private int totalProgress;

        ProgressLoggerImpl(
            ProgressLoggerImpl parent,
            OperationIdentifier progressOperationId,
            String category,
            ProgressListener listener,
            Clock clock,
            boolean buildOperationStart,
            @Nullable OperationIdentifier buildOperationId,
            @Nullable OperationIdentifier parentBuildOperationId,
            @Nullable BuildOperationCategory buildOperationCategory
        ) {
            this.parent = parent;
            this.progressOperationId = progressOperationId;
            this.category = category;
            this.listener = listener;
            this.clock = clock;
            this.buildOperationStart = buildOperationStart;
            this.buildOperationId = buildOperationId;
            this.parentBuildOperationId = parentBuildOperationId;
            this.buildOperationCategory = buildOperationCategory;
        }

        @Override
        public String toString() {
            return category + " - " + description;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public ProgressLogger setDescription(String description) {
            assertCanConfigure();
            this.description = description;
            return this;
        }

        @Override
        public String getShortDescription() {
            return null;
        }

        @Override
        public ProgressLogger setShortDescription(String shortDescription) {
            assertCanConfigure();
            return this;
        }

        @Override
        public String getLoggingHeader() {
            return loggingHeader;
        }

        @Override
        public ProgressLogger setLoggingHeader(String loggingHeader) {
            assertCanConfigure();
            this.loggingHeader = loggingHeader;
            return this;
        }

        @Override
        public ProgressLogger start(String description, String status) {
            setDescription(description);
            started(status);
            return this;
        }

        @Override
        public void started() {
            started(null);
        }

        @Override
        public void started(String status) {
            started(status, totalProgress);
        }

        private void started(String status, int totalProgress) {
            if (!GUtil.isTrue(description)) {
                throw new IllegalStateException("A description must be specified before this operation is started.");
            }
            assertNotStarted();
            state = State.started;
            previous = current.get();
            OperationIdentifier parentProgressId;
            if (parent == null) {
                if (previous != null) {
                    parent = previous;
                    parentProgressId = parent.progressOperationId;
                } else if (buildOperationStart) {
                    parentProgressId = parentBuildOperationId;
                } else {
                    parentProgressId = buildOperationId;
                }
            } else {
                parentProgressId = parent.progressOperationId;
                parent.assertRunning();
            }
            current.set(this);
            listener.started(new ProgressStartEvent(
                progressOperationId,
                parentProgressId,
                clock.getCurrentTime(),
                category,
                description,
                loggingHeader,
                ensureNotNull(status),
                totalProgress,
                buildOperationStart,
                buildOperationId,
                buildOperationCategory
            ));
        }

        public void progress(String status) {
            progress(status, false);
        }

        public void progress(String status, boolean failing) {
            assertRunning();
            listener.progress(new ProgressEvent(progressOperationId, ensureNotNull(status), failing));
        }

        public void completed() {
            completed(null, false);
        }

        public void completed(String status, boolean failed) {
            assertRunning();
            state = State.completed;
            current.set(previous);
            listener.completed(new ProgressCompleteEvent(progressOperationId, clock.getCurrentTime(), ensureNotNull(status), failed));
        }

        private String ensureNotNull(String status) {
            return status == null ? "" : status;
        }

        private void assertNotStarted() {
            if (state == State.started) {
                throw new IllegalStateException(String.format("This operation (%s) has already been started.", this));
            }
            if (state == State.completed) {
                throw new IllegalStateException(String.format("This operation (%s) has already completed.", this));
            }
        }

        private void assertRunning() {
            if (state == State.idle) {
                throw new IllegalStateException(String.format("This operation (%s) has not been started.", this));
            }
            if (state == State.completed) {
                throw new IllegalStateException(String.format("This operation (%s) has already been completed.", this));
            }
        }

        private void assertCanConfigure() {
            if (state != State.idle) {
                throw new IllegalStateException(String.format("Cannot configure this operation (%s) once it has started.", this));
            }
        }

    }

    private static class NoopProgressLogger implements ProgressLogger {
        private String description;
        private String shortDescription;
        private String loggingHeader;

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public ProgressLogger setDescription(String description) {
            this.description = description;
            return this;
        }

        @Override
        public String getShortDescription() {
            return shortDescription;
        }

        @Override
        public ProgressLogger setShortDescription(String description) {
            this.shortDescription = description;
            return this;
        }

        @Override
        public String getLoggingHeader() {
            return loggingHeader;
        }

        @Override
        public ProgressLogger setLoggingHeader(String header) {
            this.loggingHeader = loggingHeader;
            return this;
        }

        @Override
        public ProgressLogger start(String description, String status) {
            this.description = description;
            return this;
        }

        @Override
        public void started() {

        }

        @Override
        public void started(String status) {

        }

        @Override
        public void progress(@Nullable String status) {

        }

        @Override
        public void progress(@Nullable String status, boolean failing) {

        }

        @Override
        public void completed() {

        }

        @Override
        public void completed(String status, boolean failed) {

        }
    }
}

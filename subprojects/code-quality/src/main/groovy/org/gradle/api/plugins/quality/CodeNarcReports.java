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

package org.gradle.api.plugins.quality;

import org.gradle.api.reporting.Report;
import org.gradle.api.reporting.ReportContainer;

/**
 * The reporting configuration for the the {@link CodeNarc} test.
 */
public interface CodeNarcReports extends ReportContainer<Report> {

    /**
     * The codenarc (single file) xml report
     *
     * @return The codenarc (single file) xml report
     */
    Report getXml();

    /**
     * The codenarc (single file) html report
     *
     * @return The codenarc (single file) html report
     */
    Report getHtml();

    /**
     * The codenarc (single file) text report
     *
     * @return The codenarc (single file) text report
     */
    Report getText();
}

package fi.helsinki.cs.tmc.core.commands;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import fi.helsinki.cs.tmc.core.CoreTestSettings;
import fi.helsinki.cs.tmc.core.TmcCore;
import fi.helsinki.cs.tmc.core.communication.ExerciseSubmitter;
import fi.helsinki.cs.tmc.core.communication.SubmissionPoller;
import fi.helsinki.cs.tmc.core.communication.TmcApi;
import fi.helsinki.cs.tmc.core.communication.UrlHelper;
import fi.helsinki.cs.tmc.core.domain.Course;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;
import fi.helsinki.cs.tmc.core.domain.submission.SubmissionResult;
import fi.helsinki.cs.tmc.core.exceptions.ExpiredException;
import fi.helsinki.cs.tmc.core.exceptions.TmcCoreException;
import fi.helsinki.cs.tmc.core.testhelpers.ExampleJson;
import fi.helsinki.cs.tmc.langs.domain.NoLanguagePluginFoundException;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;


import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class SubmitTest {


    private Submit submit;
    private ExerciseSubmitter submitterMock;
    private CoreTestSettings settings;
    private String submissionUrl;
    private ProgressObserver observer;

    @Rule public WireMockRule wireMock = new WireMockRule(0);
    private String serverAddress = "http://127.0.0.1:";

    private Submit submitWithObserver;
    private SubmissionPoller pollerMock;
    private String path;

    private void createSettings() {
        settings = new CoreTestSettings();
        settings.setUsername("Samu");
        settings.setPassword("Bossman");
        settings.setCurrentCourse(new Course());
        settings.setApiVersion("7");
    }

    private void buildWireMock() throws URISyntaxException {
        wireMock.stubFor(
                post(urlPathEqualTo("/exercises/1231/submissions.json"))
                        .willReturn(
                                WireMock.aResponse()
                                        .withStatus(200)
                                        .withBody(
                                                ExampleJson.failedSubmitResponse.replace(
                                                        "https://tmc.mooc.fi/staging",
                                                        serverAddress)
                                                        .replaceAll(
                                                                "8080",
                                                                String.valueOf(wireMock.port())))));

        wireMock.stubFor(
                get(urlPathEqualTo("/submissions/7777.json"))
                        .willReturn(
                                WireMock.aResponse()
                                        .withStatus(200)
                                        .withBody(
                                                ExampleJson.failedSubmission
                                                        .replace(
                                                                "https://tmc.mooc.fi/staging",
                                                                serverAddress)
                                                        .replaceAll(
                                                                "8080",
                                                                String.valueOf(wireMock.port())))));

        wireMock.stubFor(
                get(urlPathEqualTo("/courses/19.json"))
                        .willReturn(
                                WireMock.aResponse()
                                        .withStatus(200)
                                        .withBody(
                                                ExampleJson.noDeadlineCourseExample.replace(
                                                        "https://tmc.mooc.fi/staging",
                                                        serverAddress))));
    }

    @Before
    public void setup() throws Exception {
        createSettings();

        serverAddress += wireMock.port();

        observer = mock(ProgressObserver.class);

        submissionUrl = new UrlHelper(settings).withParams("/submissions/1781.json");

        submitterMock = mock(ExerciseSubmitter.class);
        pollerMock = mock(SubmissionPoller.class);

        path = Paths.get("polku", "kurssi", "kansioon", "src").toString();

        when(submitterMock.submit(anyString())).thenReturn(serverAddress + submissionUrl);
        submit =
                new Submit(
                        settings, submitterMock, new SubmissionPoller(new TmcApi(settings)), path);
        submitWithObserver = new Submit(settings, submitterMock, pollerMock, path, observer);
    }

    @Test(expected = TmcCoreException.class)
    public void testThrowsExceptionIfNoUsername() throws Exception {
        settings.setUsername(null);
        new Submit(settings, null, null, "").call();
    }

    @Test(expected = TmcCoreException.class)
    public void testThrowsExceptionIfNoPassword() throws Exception {
        settings.setPassword(null);
        new Submit(settings, null, null, "").call();
    }

    @Test
    public void testHandlesSuccessfulTestRunResponseCorrectly() throws Exception {
        wireMock.stubFor(
                get(urlEqualTo(submissionUrl))
                        .willReturn(
                                WireMock.aResponse()
                                        .withStatus(200)
                                        .withBody(ExampleJson.successfulSubmission)));

        SubmissionResult submissionResult = submit.call();
        assertNotNull(submissionResult);
        assertTrue(submissionResult.isAllTestsPassed());
    }

    @Test
    public void testHandlesUnsuccessfulTestRunResponseCorrectly() throws Exception {
        wireMock.stubFor(
                get(urlEqualTo(submissionUrl))
                        .willReturn(
                                WireMock.aResponse()
                                        .withStatus(200)
                                        .withBody(ExampleJson.failedSubmission)));

        SubmissionResult submissionResult = submit.call();
        assertNotNull(submissionResult);
        assertFalse(submissionResult.isAllTestsPassed());
    }

    //TODO: Move to TmcCoreTest or delete
    @Test
    public void submitWithTmcCore() throws Exception {
        buildWireMock();

        CoreTestSettings settings = new CoreTestSettings("test", "1234", serverAddress);
        TmcApi tmcApi = new TmcApi(settings);
        Course course = tmcApi.getCourseFromString(ExampleJson.noDeadlineCourseExample);
        settings.setCurrentCourse(course);
        TmcCore core = new TmcCore(settings);

        Path path =
                Paths.get("testResources", "halfdoneExercise", "viikko1", "Viikko1_004.Muuttujat");
        ListenableFuture<SubmissionResult> submit = core.submit(path);
        final List<SubmissionResult> result = new ArrayList<>();
        Futures.addCallback(
                submit,
                new FutureCallback<SubmissionResult>() {

                    @Override
                    public void onSuccess(SubmissionResult sub) {
                        result.add(sub);
                    }

                    @Override
                    public void onFailure(Throwable thrwbl) {
                        System.out.println("VIRHE: " + thrwbl);
                        thrwbl.printStackTrace();
                    }
                });
        while (!submit.isDone()) {
            Thread.sleep(100);
        }
        assertFalse(result.isEmpty());
        assertFalse(result.get(0).isAllTestsPassed());
    }

    @Test
    public void testSubmissionWithObserver()
            throws TmcCoreException, IOException, ParseException, ExpiredException,
                    IllegalArgumentException, InterruptedException, URISyntaxException,
                    NoLanguagePluginFoundException {

        when(submitterMock.submit(eq(path), eq(observer))).thenReturn("xkcd.com");
        submitWithObserver.call();
        verify(submitterMock).submit(eq(path), eq(observer));
        verify(pollerMock).getSubmissionResult(eq("xkcd.com"), eq(observer));
    }

}

package com.redhat.pantheon.servlet;

import com.redhat.pantheon.extension.Events;
import com.redhat.pantheon.helper.PantheonConstants;
import com.redhat.pantheon.model.assembly.Assembly;
import com.redhat.pantheon.model.assembly.AssemblyVersion;
import com.redhat.pantheon.model.module.Module;
import com.redhat.pantheon.model.module.ModuleVersion;
import com.redhat.pantheon.servlet.UnpublishVersion;
import com.redhat.pantheon.sling.ServiceResourceResolverProvider;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.servlets.post.HtmlResponse;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.ModificationType;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;

import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;
import static com.redhat.pantheon.util.TestUtils.registerMockAdapter;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({SlingContextExtension.class})
class UnpublishVersionTest {

    SlingContext slingContext = new SlingContext(ResourceResolverType.JCR_OAK);
    String testHTML = "<!DOCTYPE html> <html lang=\"en\"> <head><title>test title</title></head> <body " +
            "class=\"article\"><h1>test title</h1> </header> </body> </html>";

    @Test
    @DisplayName("doRun for module with only released version")
    void doRun() throws Exception {
        // Given
        slingContext.create()
                .resource("/content/repositories/repo/module/en_US/variants/DEFAULT/released",
                        "jcr:primaryType", "pant:moduleVersion");
        slingContext.create()
                .resource("/content/repositories/repo/module/en_US/variants/DEFAULT/released/metadata",
                        "jcr:title", "A published title");
        slingContext.create()
                .resource("/content/repositories/repo/module/en_US/variants/DEFAULT/released/cached_html/jcr:content",
                        "jcr:data", "Released content");
        slingContext.create()
                .resource("/content/repositories/repo/module/en_US/source/released/jcr:content",
                        "jcr:data", "Released content");
        Map<String, Object> map = new HashMap<>();
        map.put("type","module");
        slingContext.request().setParameterMap(map);
        registerMockAdapter(Module.class, slingContext);
        registerMockAdapter(ModuleVersion.class, slingContext);
        ServiceResourceResolverProvider serviceResourceResolverProvider = Mockito.mock(ServiceResourceResolverProvider.class);
        ResourceResolver resourceResolver = slingContext.request().getResourceResolver();
        Module module = slingContext.request().adaptTo(Module.class);
        lenient().doReturn(resourceResolver)
                .when(serviceResourceResolverProvider).getServiceResourceResolver();
        Events events = mock(Events.class);
        HtmlResponse postResponse = new HtmlResponse();
        List<Modification> changes = newArrayList();
        slingContext.request().setResource( slingContext.resourceResolver().getResource("/content/repositories/repo/module") );
        UnpublishVersion operation = new UnpublishVersion(events, serviceResourceResolverProvider);

        // When
        operation.doRun(slingContext.request(), postResponse, changes);

        // Then
        assertEquals(1, changes.size());
        assertEquals(ModificationType.MODIFY, changes.get(0).getType());
        assertEquals("/content/repositories/repo/module", changes.get(0).getSource());
        assertEquals(HttpServletResponse.SC_OK, postResponse.getStatusCode());
        assertNull(slingContext.resourceResolver().getResource("/content/repositories/repo/module/en_US/variants/DEFAULT/released"));
        assertNotNull(slingContext.resourceResolver().getResource("/content/repositories/repo/module/en_US/variants/DEFAULT/draft"));
        assertNull(slingContext.resourceResolver().getResource("/content/repositories/repo/module/en_US/source/released"));

    }

    @Test
    @DisplayName("doRun for module with both released and draft version")
    void doRunWithDraftVersoin() throws Exception {
        // Given
        slingContext.create()
                .resource("/content/repositories/repo/module/en_US/variants/DEFAULT/released",
                        "jcr:primaryType", "pant:moduleVersion");
        slingContext.create()
                .resource("/content/repositories/repo/module/en_US/variants/DEFAULT/released/metadata",
                        "jcr:title", "A published title");
        slingContext.create()
                .resource("/content/repositories/repo/module/en_US/variants/DEFAULT/released/cached_html/jcr:content",
                        "jcr:data", "Released content");
        slingContext.create()
                .resource("/content/repositories/repo/module/en_US/variants/DEFAULT/draft/cached_html/jcr:content",
                "jcr:data", "Draft content");
        slingContext.create()
                .resource("/content/repositories/repo/module/en_US/source/draft/jcr:content",
                "jcr:data", "Draft content");
        slingContext.create()
                .resource("/content/repositories/repo/module/en_US/source/released/jcr:content",
                        "jcr:data", "Released content");
        Map<String, Object> map = new HashMap<>();
        map.put("type","module");
        slingContext.request().setParameterMap(map);
        registerMockAdapter(Module.class, slingContext);
        registerMockAdapter(ModuleVersion.class, slingContext);
        Events events = mock(Events.class);
        ServiceResourceResolverProvider serviceResourceResolverProvider = Mockito.mock(ServiceResourceResolverProvider.class);
        ResourceResolver resourceResolver = slingContext.request().getResourceResolver();
        Module module = slingContext.request().adaptTo(Module.class);
        lenient().doReturn(resourceResolver)
                .when(serviceResourceResolverProvider).getServiceResourceResolver();
        HtmlResponse postResponse = new HtmlResponse();
        List<Modification> changes = newArrayList();
        slingContext.request().setResource( slingContext.resourceResolver().getResource("/content/repositories/repo/module") );
        UnpublishVersion operation = new UnpublishVersion(events,serviceResourceResolverProvider);

        // When
        operation.doRun(slingContext.request(), postResponse, changes);

        // Then
        assertEquals(1, changes.size());
        assertEquals(ModificationType.MODIFY, changes.get(0).getType());
        assertEquals("/content/repositories/repo/module", changes.get(0).getSource());
        assertEquals(HttpServletResponse.SC_OK, postResponse.getStatusCode());
        assertNull(slingContext.resourceResolver().getResource("/content/repositories/repo/module/en_US/variants/DEFAULT/released"));
        assertNotNull(slingContext.resourceResolver().getResource("/content/repositories/repo/module/en_US/variants/DEFAULT/draft"));
        assertNull(slingContext.resourceResolver().getResource("/content/repositories/repo/module/en_US/source/released"));
        assertNotNull(slingContext.resourceResolver().getResource("/content/repositories/repo/module/en_US/source/draft"));

    }

    @Test
    @DisplayName("doRun for module with no released version")
    void doRunNoDraftVersion() throws Exception {
        // Given
        slingContext.build()
                .resource("/content/repositories/repo/module/en_US/variants/DEFAULT/draft/metadata")
                .resource("/content/repositories/repo/module/en_US/variants/DEFAULT/draft/cached_html/jcr:content")
                .commit();
        Map<String, Object> map = new HashMap<>();
        map.put("type","module");
        slingContext.request().setParameterMap(map);
        registerMockAdapter(Module.class, slingContext);
        HtmlResponse postResponse = new HtmlResponse();
        List<Modification> changes = newArrayList();
        ServiceResourceResolverProvider serviceResourceResolverProvider = Mockito.mock(ServiceResourceResolverProvider.class);
        ResourceResolver resourceResolver = slingContext.request().getResourceResolver();
        Module module = slingContext.request().adaptTo(Module.class);
        lenient().doReturn(resourceResolver)
                .when(serviceResourceResolverProvider).getServiceResourceResolver();
        slingContext.request().setResource( slingContext.resourceResolver().getResource("/content/repositories/repo/module") );
        UnpublishVersion operation = new UnpublishVersion(null, serviceResourceResolverProvider);

        // When
        operation.doRun(slingContext.request(), postResponse, changes);

        // Then
        assertTrue(changes.size() == 0);
        assertEquals(HttpServletResponse.SC_PRECONDITION_FAILED, postResponse.getStatusCode());
        assertNull(slingContext.resourceResolver().getResource("/content/repositories/repo/module/en_US/variants/DEFAULT/released"));
        assertNull(slingContext.resourceResolver().getResource("/content/repositories/repo/module/en_US/source/released"));

    }
    @Test
    @DisplayName("doRun for assembly with only released version")
    void doRunForAssembly() throws Exception {
        // Given
        slingContext.build()
                .resource("/content/repositories/rhel-8-docs/entities/assemblies/changes",
                        "jcr:primaryType", "pant:assembly")
                .resource("/content/repositories/rhel-8-docs/entities/assemblies/changes/en_US/source/draft/jcr:content","jcr:data", "Draft content")
                .resource("/content/repositories/rhel-8-docs/entities/assemblies/changes/en_US/source/released/jcr:content","jcr:data", "Released content")
                .resource("/content/repositories/rhel-8-docs/entities/assemblies/changes/en_US/variants/DEFAULT/draft/metadata",
                        "jcr:title", "A title",
                        "jcr:description", "A description")
                .resource("/content/repositories/rhel-8-docs/entities/assemblies/changes/en_US/variants/DEFAULT/draft/jcr:content",
                        "jcr:data", "This is the source content")
                .resource("/content/repositories/rhel-8-docs/entities/assemblies/changes/en_US/variants/DEFAULT/draft/cached_html/jcr:content",
                        "jcr:data", testHTML)
                .resource("/content/repositories/rhel-8-docs/entities/assemblies/changes/en_US/variants/DEFAULT/released/metadata",
                        "jcr:title", "A title",
                        "jcr:description", "A description")
                .resource("/content/repositories/rhel-8-docs/entities/assemblies/changes/en_US/variants/DEFAULT/released/jcr:content",
                        "jcr:data", "This is the source content")
                .resource("/content/repositories/rhel-8-docs/entities/assemblies/changes/en_US/variants/DEFAULT/released/cached_html/jcr:content",
                        "jcr:data", testHTML)
                .commit();
        registerMockAdapter(Assembly.class, slingContext);
        slingContext.request().setResource( slingContext.resourceResolver().getResource("/content/repositories/rhel-8-docs/entities/assemblies/changes") );
        Map<String, Object> map = new HashMap<>();
        map.put("type","assembly");
        slingContext.request().setParameterMap(map);
        Assembly assembly = slingContext.request().getResourceResolver().getResource("/content/repositories/rhel-8-docs/entities/assemblies/changes").adaptTo(Assembly.class);
        registerMockAdapter(AssemblyVersion.class, slingContext);
        ServiceResourceResolverProvider serviceResourceResolverProvider = Mockito.mock(ServiceResourceResolverProvider.class);
        ResourceResolver resourceResolver = slingContext.request().getResourceResolver();
        lenient().doReturn(resourceResolver)
                .when(serviceResourceResolverProvider).getServiceResourceResolver();
        Events events = mock(Events.class);
        HtmlResponse postResponse = new HtmlResponse();
        List<Modification> changes = newArrayList();
        UnpublishVersion operation = new UnpublishVersion(events, serviceResourceResolverProvider);

        // When
        operation.doRun(slingContext.request(), postResponse, changes);

        // Then
        assertEquals(1, changes.size());
        assertEquals(ModificationType.MODIFY, changes.get(0).getType());
        assertEquals("/content/repositories/rhel-8-docs/entities/assemblies/changes", changes.get(0).getSource());
        assertEquals(HttpServletResponse.SC_OK, postResponse.getStatusCode());
        assertNull(slingContext.resourceResolver().getResource("\"/content/repositories/rhel-8-docs/entities/assemblies/changes/en_US/variants/DEFAULT/released/"));
        assertNotNull(slingContext.resourceResolver().getResource("/content/repositories/rhel-8-docs/entities/assemblies/changes/en_US/variants/DEFAULT/draft"));
        assertNull(slingContext.resourceResolver().getResource("/content/repositories/rhel-8-docs/entities/assemblies/changes/en_US/source/released"));

    }

    @Test
    @DisplayName("doRun for assembly with both released and draft version")
    void doRunforAssemblyWithDraftVersion() throws Exception {
        // Given
        slingContext.create()
                .resource("/content/repositories/repo/assembly/en_US/variants/DEFAULT/released",
                        "jcr:primaryType", "pant:moduleVersion");
        slingContext.create()
                .resource("/content/repositories/repo/assembly/en_US/variants/DEFAULT/released/metadata",
                        "jcr:title", "A published title");
        slingContext.create()
                .resource("/content/repositories/repo/assembly/en_US/variants/DEFAULT/released/cached_html/jcr:content",
                        "jcr:data", "Released content");
        slingContext.create()
                .resource("/content/repositories/repo/assembly/en_US/variants/DEFAULT/draft/cached_html/jcr:content",
                "jcr:data", "Draft content");
        slingContext.create()
                .resource("/content/repositories/repo/assembly/en_US/source/draft/jcr:content",
                "jcr:data", "Draft content");
        slingContext.create()
                .resource("/content/repositories/repo/assembly/en_US/source/released/jcr:content",
                        "jcr:data", "Released content");
        slingContext.request().setResource( slingContext.resourceResolver().getResource("/content/repositories/repo/assembly") );
        Map<String, Object> map = new HashMap<>();
        map.put("type","assembly");
        slingContext.request().setParameterMap(map);
        registerMockAdapter(Assembly.class, slingContext);
        registerMockAdapter(AssemblyVersion.class, slingContext);
        Events events = mock(Events.class);
        ServiceResourceResolverProvider serviceResourceResolverProvider = Mockito.mock(ServiceResourceResolverProvider.class);
        ResourceResolver resourceResolver = slingContext.request().getResourceResolver();
        lenient().doReturn(resourceResolver)
                .when(serviceResourceResolverProvider).getServiceResourceResolver();
        HtmlResponse postResponse = new HtmlResponse();
        List<Modification> changes = newArrayList();
        slingContext.request().setResource( slingContext.resourceResolver().getResource("/content/repositories/repo/assembly") );
        UnpublishVersion operation = new UnpublishVersion(events,serviceResourceResolverProvider);

        // When
        operation.doRun(slingContext.request(), postResponse, changes);

        // Then
        assertEquals(1, changes.size());
        assertEquals(ModificationType.MODIFY, changes.get(0).getType());
        assertEquals("/content/repositories/repo/assembly", changes.get(0).getSource());
        assertEquals(HttpServletResponse.SC_OK, postResponse.getStatusCode());
        assertNull(slingContext.resourceResolver().getResource("/content/repositories/repo/assembly/en_US/variants/DEFAULT/released"));
        assertNotNull(slingContext.resourceResolver().getResource("/content/repositories/repo/assembly/en_US/variants/DEFAULT/draft"));
        assertNull(slingContext.resourceResolver().getResource("/content/repositories/repo/assembly/en_US/source/released"));
        assertNotNull(slingContext.resourceResolver().getResource("/content/repositories/repo/assembly/en_US/source/draft"));

    }

    @Test
    @DisplayName("doRun for assembly with no released version")
    void doRunForAssemblyWithNoDraftVersion() throws Exception {
        // Given
        slingContext.build()
                .resource("/content/repositories/repo/assembly/en_US/variants/DEFAULT/draft/metadata")
                .resource("/content/repositories/repo/assembly/en_US/variants/DEFAULT/draft/cached_html/jcr:content")
                .commit();
        slingContext.request().setResource( slingContext.resourceResolver().getResource("/content/repositories/repo/assembly") );
        Map<String, Object> map = new HashMap<>();
        map.put("type","assembly");
        slingContext.request().setParameterMap(map);
        registerMockAdapter(Assembly.class, slingContext);
        HtmlResponse postResponse = new HtmlResponse();
        List<Modification> changes = newArrayList();
        ServiceResourceResolverProvider serviceResourceResolverProvider = Mockito.mock(ServiceResourceResolverProvider.class);
        ResourceResolver resourceResolver = slingContext.request().getResourceResolver();
        lenient().doReturn(resourceResolver)
                .when(serviceResourceResolverProvider).getServiceResourceResolver();
        slingContext.request().setResource( slingContext.resourceResolver().getResource("/content/repositories/repo/assembly") );
        UnpublishVersion operation = new UnpublishVersion(null, serviceResourceResolverProvider);

        // When
        operation.doRun(slingContext.request(), postResponse, changes);

        // Then
        assertTrue(changes.size() == 0);
        assertEquals(HttpServletResponse.SC_PRECONDITION_FAILED, postResponse.getStatusCode());
        assertNull(slingContext.resourceResolver().getResource("/content/repositories/repo/assembly/en_US/variants/DEFAULT/released"));
        assertNull(slingContext.resourceResolver().getResource("/content/repositories/repo/assembly/en_US/source/released"));

    }
}

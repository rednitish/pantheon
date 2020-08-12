package com.redhat.pantheon.servlet;

import com.redhat.pantheon.asciidoctor.AsciidoctorService;
import com.redhat.pantheon.conf.GlobalConfig;
import com.redhat.pantheon.extension.Events;
import com.redhat.pantheon.extension.events.assembly.AssemblyVersionPublishedEvent;
import com.redhat.pantheon.extension.events.module.ModuleVersionPublishedEvent;
import com.redhat.pantheon.helper.PantheonConstants;
import com.redhat.pantheon.model.HashableFileResource;
import com.redhat.pantheon.model.api.FileResource;
import com.redhat.pantheon.model.assembly.Assembly;
import com.redhat.pantheon.model.assembly.AssemblyLocale;
import com.redhat.pantheon.model.assembly.AssemblyVariant;
import com.redhat.pantheon.model.assembly.AssemblyVersion;
import com.redhat.pantheon.model.module.*;
import com.redhat.pantheon.sling.ServiceResourceResolverProvider;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.jcr.base.util.AccessControlUtil;
import org.apache.sling.servlets.post.*;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

import static com.redhat.pantheon.jcr.JcrResources.rename;
import static com.redhat.pantheon.model.api.util.ResourceTraversal.traverseFrom;
import static com.redhat.pantheon.servlet.ServletUtils.paramValue;
import static com.redhat.pantheon.servlet.ServletUtils.paramValueAsLocale;

@Component(
        service = PostOperation.class,
        property = {
                Constants.SERVICE_DESCRIPTION + "=Releases the latest draft version of a module",
                Constants.SERVICE_VENDOR + "=Red Hat Content Tooling team",
                PostOperation.PROP_OPERATION_NAME + "=pant:publish"
        })
public class PublishDraftVersion extends AbstractPostOperation {

    private Events events;
    private AsciidoctorService asciidoctorService;
    private ServiceResourceResolverProvider serviceResourceResolverProvider;
    private Logger logger = LoggerFactory.getLogger(PublishDraftVersion.class);

    @Activate
    public PublishDraftVersion(@Reference Events events,
                               @Reference AsciidoctorService asciidoctorService,
                               @Reference ServiceResourceResolverProvider serviceResourceResolverProvider) {
        this.events = events;
        this.asciidoctorService = asciidoctorService;
        this.serviceResourceResolverProvider = serviceResourceResolverProvider;
    }

    private Assembly getAssembly(SlingHttpServletRequest request) {
        return request.getResource().adaptTo(Assembly.class);
    }

    private Module getModule(SlingHttpServletRequest request) {
        return request.getResource().adaptTo(Module.class);
    }

    private Locale getLocale(SlingHttpServletRequest request) {
        return paramValueAsLocale(request, "locale", GlobalConfig.DEFAULT_MODULE_LOCALE);
    }

    private String getVariant(SlingHttpServletRequest request) {
        return paramValue(request, "variant", ModuleVariant.DEFAULT_VARIANT_NAME);
    }

    @Override
    public void run(SlingHttpServletRequest request, PostResponse response, SlingPostProcessor[] processors) {
        logger.debug("Operation Publishinging draft version started");
        long startTime = System.currentTimeMillis();
        super.run(request, response, processors);
        if (response.getError() == null) {
            // call the extension point
            Locale locale = getLocale(request);
            String variant = getVariant(request);
            if(request.getRequestParameter("type").equals("assembly")) {
                AssemblyVersion assemblyVersion = getAssembly(request).locale(locale).get()
                        .variants().get()
                        .variant(variant).get()
                        .released().get();
                // Regenerate the module once more
                asciidoctorService.getModuleHtml(getAssembly(request), locale, variant, false, new HashMap(), true);
                events.fireEvent(new AssemblyVersionPublishedEvent(assemblyVersion), 15);

            }
            else {
                ModuleVersion moduleVersion =  getModule(request).locale(locale).get()
                        .variants().get()
                        .variant(variant).get()
                        .released().get();
                asciidoctorService.getModuleHtml(getModule(request), locale, variant, false, new HashMap(), true);
                events.fireEvent(new ModuleVersionPublishedEvent(moduleVersion), 15);
            }
        }
        log.debug("Operation Publishinging draft version,  completed");
        long elapseTime = System.currentTimeMillis() - startTime;
        log.debug("Total elapsed http request/response time in milliseconds: " + elapseTime);
    }

    @Override
    protected void doRun(SlingHttpServletRequest request, PostResponse response, List<Modification> changes) throws  RepositoryException{
        try {
            boolean canPublish = false;
            Session session = request.getResourceResolver().adaptTo(Session.class);
            UserManager userManager = AccessControlUtil.getUserManager(session);
            Iterator<Group> groupIterator = userManager.getAuthorizable(session.getUserID()).memberOf();
            while (groupIterator.hasNext()) {
                Authorizable group = groupIterator.next();
                if (group.isGroup() && PantheonConstants.PANTHEON_PUBLISHERS.equalsIgnoreCase(group.getID())) {
                    canPublish = true;
                    break;
                }
            }
            ResourceResolver serviceResourceResolver = request.getResourceResolver();
            if(canPublish) {
                serviceResourceResolver = serviceResourceResolverProvider.getServiceResourceResolver();
            }

            // if request constitues of Assembly, publish Assembly
            if (request.getRequestParameter(PantheonConstants.TYPE).getString().equals(PantheonConstants.ASSEMBLY))
                publishAssembly(request, response, changes, serviceResourceResolver);
            // else publish it as module
            else
               publishModule(request, response, changes, serviceResourceResolver);

            if (serviceResourceResolver.hasChanges()) {
                serviceResourceResolver.commit();
            }

        }catch (Exception ex){
            logger.error("Error occured, rolled back last changes", ex.getMessage());
        }

    }

    /**
     * Method to check if request contains a Module and it needs to get published
     *
     * @param request
     * @param response
     * @param changes
     * @param serviceResourceResolver
     * @throws PersistenceException
     */
    private void publishModule(SlingHttpServletRequest request, PostResponse response, List<Modification> changes, ResourceResolver serviceResourceResolver) throws PersistenceException {
        Module module = serviceResourceResolver.getResource(request.getResource().getPath()).adaptTo(Module.class);
        Locale locale = getLocale(request);
        String variant = getVariant(request);
        // Get the draft version, there should be one
        Optional<ModuleVersion> versionToRelease = module.getDraftVersion(locale, variant);
        if (!versionToRelease.isPresent()) {
            response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED,
                    "The module doesn't have a draft version to be released");
            return ;
        } else if (versionToRelease.get().metadata().getOrCreate().productVersion().get() == null
                || versionToRelease.get().metadata().getOrCreate().productVersion().get().isEmpty()) {
            // Check if productVersion is set
            response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED,
                    "The version to be released doesn't have productVersion metadata");
            return ;
        } else if (versionToRelease.get().metadata().getOrCreate().urlFragment().get() == null
                || versionToRelease.get().metadata().getOrCreate().urlFragment().get().isEmpty()) {
            // Check if urlFragment is set
            response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED,
                    "The version to be released doesn't have urlFragment metadata");
            return ;
        }
        // Draft becomes the new released version
        ModuleVariant moduleVariant = traverseFrom(module)
                .toChild(m -> module.locale(locale))
                .toChild(ModuleLocale::variants)
                .toChild(variants -> variants.variant(variant))
                .get();
        moduleVariant.releaseDraft();
        changes.add(Modification.onModified(module.getPath()));
        // source/draft becomes source/released
        FileResource draftSource = traverseFrom(module)
                .toChild(m -> module.locale(locale))
                .toChild(ModuleLocale::source)
                .toChild(sourceContent -> sourceContent.draft())
                .get();
        // Check for released version
        Optional<HashableFileResource> releasedSource = traverseFrom(module)
                .toChild(m -> module.locale(locale))
                .toChild(ModuleLocale::source)
                .toChild(sourceContent -> sourceContent.released())
                .getAsOptional();
        if (draftSource != null) {
            if (releasedSource.isPresent()) {
                try {
                    releasedSource.get().delete();
                } catch (PersistenceException e) {
                    throw new RuntimeException("Failed to remove source/released: " + releasedSource.get().getPath());
                }
            }
            try {
                rename(draftSource, "released");
            } catch (RepositoryException e) {
                throw new RuntimeException("Cannot find source/draft: " + draftSource.getPath());
            }

    }
    }

    /**
     *  Method to check if request contains an assembly and it needs to get published
     *
     * @param request
     * @param response
     * @param changes
     * @param serviceResourceResolver
     * @return
     * @throws PersistenceException
     */
    private void publishAssembly(SlingHttpServletRequest request, PostResponse response, List<Modification> changes, ResourceResolver serviceResourceResolver) throws PersistenceException {
            Assembly assembly = serviceResourceResolver.getResource(request.getResource().getPath()).adaptTo(Assembly.class);
            Locale locale = getLocale(request);
            String variant = getVariant(request);
            // Get the draft version, there should be one
            Optional<AssemblyVersion> versionToRelease = assembly.getDraftVersion(locale, variant);
            if (!versionToRelease.isPresent()) {
                response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED,
                        "The module doesn't have a draft version to be released");
                return ;
            }
            else if (versionToRelease.get().metadata().getOrCreate().productVersion().get() == null
                    || versionToRelease.get().metadata().getOrCreate().productVersion().get().isEmpty()) {
                // Check if productVersion is set
                response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED,
                        "The version to be released doesn't have productVersion metadata");
                return ;
            } else if (versionToRelease.get().metadata().getOrCreate().urlFragment().get() == null
                    || versionToRelease.get().metadata().getOrCreate().urlFragment().get().isEmpty()) {
                // Check if urlFragment is set
                response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED,
                        "The version to be released doesn't have urlFragment metadata");
                return ;
            }
                // Draft becomes the new released version
                AssemblyVariant assemblyVariant = traverseFrom(assembly)
                        .toChild(m -> assembly.locale(locale))
                        .toChild(AssemblyLocale::variants)
                        .toChild(variants -> variants.variant(variant))
                        .get();
                assemblyVariant.releaseDraft();
                changes.add(Modification.onModified(assembly.getPath()));
                // source/draft becomes source/released
                FileResource draftSource = traverseFrom(assembly)
                        .toChild(m -> assembly.locale(locale))
                        .toChild(AssemblyLocale::source)
                        .toChild(sourceContent -> sourceContent.draft())
                        .get();
                // Check for released version
                Optional<HashableFileResource> releasedSource = traverseFrom(assembly)
                        .toChild(m -> assembly.locale(locale))
                        .toChild(AssemblyLocale::source)
                        .toChild(sourceContent -> sourceContent.released())
                        .getAsOptional();
                if (draftSource != null) {
                    if (releasedSource.isPresent()) {
                        try {
                            releasedSource.get().delete();
                        } catch (PersistenceException e) {
                            throw new RuntimeException("Failed to remove source/released: " + releasedSource.get().getPath());
                        }
                    }
                    try {
                        rename(draftSource, "released");
                    } catch (RepositoryException e) {
                        throw new RuntimeException("Cannot find source/draft: " + draftSource.getPath());
                    }
                }
            return ;
    }
}
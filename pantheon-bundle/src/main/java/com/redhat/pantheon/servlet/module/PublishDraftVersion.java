package com.redhat.pantheon.servlet.module;

import com.redhat.pantheon.asciidoctor.AsciidoctorService;
import com.redhat.pantheon.conf.GlobalConfig;
import com.redhat.pantheon.extension.Events;
import com.redhat.pantheon.extension.events.ModuleVersionPublishedEvent;
import com.redhat.pantheon.model.module.Module;
import com.redhat.pantheon.model.module.ModuleLocale;
import com.redhat.pantheon.model.module.ModuleVariant;
import com.redhat.pantheon.model.module.ModuleVersion;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.servlets.post.AbstractPostOperation;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.PostOperation;
import org.apache.sling.servlets.post.PostResponse;
import org.apache.sling.servlets.post.SlingPostProcessor;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.http.HttpServletResponse;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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

    @Activate
    public PublishDraftVersion(@Reference Events events,
                               @Reference AsciidoctorService asciidoctorService) {
        this.events = events;
        this.asciidoctorService = asciidoctorService;
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
        super.run(request, response, processors);
        if (response.getError() == null) {
            // call the extension point
            Locale locale = getLocale(request);
            Module module = getModule(request);
            String variant = getVariant(request);
            ModuleLocale moduleLocale = module.getModuleLocale(locale);


            //FIXME - this is a hack that needs to be removed when we have the attribute placeholder logic implemented
            Optional<ModuleVersion> versionToRelease = module.getReleasedVersion(locale, variant);
            asciidoctorService.getModuleHtml(versionToRelease.get(), module, new HashMap(), true);

            events.fireEvent(new ModuleVersionPublishedEvent(moduleLocale.getPath()), 15);
        }
    }

    @Override
    protected void doRun(SlingHttpServletRequest request, PostResponse response, List<Modification> changes) {
        Locale locale = getLocale(request);
        Module module = getModule(request);
        String variant = getVariant(request);

        // Get the draft version, there should be one
        Optional<ModuleVersion> versionToRelease = module.getDraftVersion(locale, variant);
        if( !versionToRelease.isPresent() ) {
            response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED,
                    "The module doesn't have a draft version to be released");
        } else if (versionToRelease.get().metadata().getOrCreate().productVersion().get() == null
        		||  versionToRelease.get().metadata().getOrCreate().productVersion().get().isEmpty()) {
        	// Check if productVersion is set
        	response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED,
                    "The version to be released doesn't have productVersion metadata");
        } else if (versionToRelease.get().metadata().getOrCreate().urlFragment().get() == null
        		||  versionToRelease.get().metadata().getOrCreate().urlFragment().get().isEmpty()) {
        	// Check if urlFragment is set
        	response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED,
                    "The version to be released doesn't have urlFragment metadata");
        } else {
            // Draft becomes the new released version
            ModuleLocale moduleLocale = module.getModuleLocale(locale);
            moduleLocale.variants()
                    .map(variantsFolder -> variantsFolder.getVariant(variant))
                    .map(Optional::get)
                    .ifPresent(ModuleVariant::releaseDraft);
//            moduleLocale.released().set( moduleLocale.draft().get() );
//            moduleLocale.draft().set( null );
            // set the published date on the released version
//            versionToRelease.get()
//                    .metadata().getOrCreate()
//                    .datePublished().set(Calendar.getInstance());
            changes.add(Modification.onModified(module.getPath()));
        }
    }
}

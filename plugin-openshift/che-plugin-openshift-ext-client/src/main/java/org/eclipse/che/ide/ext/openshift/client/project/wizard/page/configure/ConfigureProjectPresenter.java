/*******************************************************************************
 * Copyright (c) 2012-2015 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.ext.openshift.client.project.wizard.page.configure;

import com.google.common.collect.FluentIterable;
import com.google.gwt.user.client.ui.AcceptsOneWidget;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.api.project.gwt.client.ProjectServiceClient;
import org.eclipse.che.api.project.shared.dto.ProjectDescriptor;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.workspace.shared.dto.ProjectConfigDto;
import org.eclipse.che.api.workspace.shared.dto.SourceStorageDto;
import org.eclipse.che.ide.api.wizard.AbstractWizardPage;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.ext.openshift.client.OpenshiftLocalizationConstant;
import org.eclipse.che.ide.ext.openshift.client.OpenshiftServiceClient;
import org.eclipse.che.ide.ext.openshift.client.dto.NewApplicationRequest;
import org.eclipse.che.ide.ext.openshift.shared.dto.ObjectMeta;
import org.eclipse.che.ide.ext.openshift.shared.dto.Project;
import org.eclipse.che.ide.ext.openshift.shared.dto.ProjectRequest;
import org.eclipse.che.ide.rest.DtoUnmarshallerFactory;
import org.eclipse.che.ide.util.NameUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;


/**
 * Presenter for configuring OpenShift project.
 *
 * @author Vlad Zhukovskiy
 */
@Singleton
public class ConfigureProjectPresenter extends AbstractWizardPage<NewApplicationRequest> implements ConfigureProjectView.ActionDelegate {

    private final ConfigureProjectView          view;
    private final OpenshiftServiceClient        openShiftClient;
    private final ProjectServiceClient          projectServiceClient;
    private final DtoUnmarshallerFactory        dtoUnmarshaller;
    private final DtoFactory                    dtoFactory;
    private final OpenshiftLocalizationConstant locale;
    private       List<String>                  openShiftProjects;
    private       List<String>                  cheProjects;

    @Inject
    public ConfigureProjectPresenter(ConfigureProjectView view,
                                     OpenshiftServiceClient openShiftClient,
                                     ProjectServiceClient projectServiceClient,
                                     DtoUnmarshallerFactory dtoUnmarshaller,
                                     DtoFactory dtoFactory,
                                     OpenshiftLocalizationConstant locale) {
        this.view = view;
        this.openShiftClient = openShiftClient;
        this.projectServiceClient = projectServiceClient;
        this.dtoUnmarshaller = dtoUnmarshaller;
        this.dtoFactory = dtoFactory;
        this.locale = locale;
        openShiftProjects = new ArrayList<>();
        cheProjects = new ArrayList<>();
        view.setDelegate(this);
    }

    /** {@inheritDoc} */
    @Override
    public void init(NewApplicationRequest dataObject) {
        super.init(dataObject);

        view.resetControls();

        openShiftClient.getProjects().then(new Operation<List<Project>>() {
                           @Override
                           public void apply(final List<Project> projects) throws OperationException {
                               openShiftProjects.clear();
                               for (Project project : projects) {
                                   openShiftProjects.add(project.getMetadata().getName());
                               }
                               view.setExistOpenShiftProjects(projects);
                           }
                       });

        projectServiceClient.getProjects(false).then(new Operation<List<ProjectDescriptor>>() {
            @Override
            public void apply(List<ProjectDescriptor> projects) throws OperationException {
                openShiftProjects.clear();
                for (ProjectDescriptor project : projects) {
                    cheProjects.add(project.getName());
                }
            }
        });
    }


    /** {@inheritDoc} */
    @Override
    public boolean isCompleted() {
        if (view.isNewOpenShiftProjectSelected()) {
            return isOpenShiftProjectNameValid(view.getOpenShiftNewProjectName()) & isCheProjectNameValid(view.getCheNewProjectName());
        } else {
            return view.getExistedSelectedProject() != null && isCheProjectNameValid(view.getCheNewProjectName());
        }
    }

    private boolean isOpenShiftProjectNameValid(String name) {
        if (openShiftProjects.contains(name)) {
            view.showOsProjectNameError(locale.existingProjectNameError());
            return false;
        }
        if (name.length() > 63
            || name.isEmpty()
            || !name.matches("[a-z0-9]([-a-z0-9]*[a-z0-9])?")) {
            view.showOsProjectNameError(locale.invalidOpenShiftProjectNameError());
            return false;
        }

        view.hideOsProjectNameError();
        return true;
    }

    private boolean isCheProjectNameValid(String projectName) {
        if (cheProjects.contains(projectName)) {
            view.showCheProjectNameError(locale.existingProjectNameError());
            return false;
        }
        if (!NameUtils.checkProjectName(projectName)) {
            view.showCheProjectNameError(locale.invalidCheProjectNameError());
            return false;
        }
        view.hideCheProjectNameError();
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public void go(AcceptsOneWidget container) {
        container.setWidget(view);
    }

    /** {@inheritDoc} */
    @Override
    public void onOpenShiftNewProjectNameChanged() {
        setUpNewProjectRequest();
        updateDelegate.updateControls();
    }

    /** {@inheritDoc} */
    @Override
    public void onCheNewProjectNameChanged() {
        setUpCheProjectRequest();
        updateDelegate.updateControls();
    }

    /** {@inheritDoc} */
    @Override
    public void onExistProjectSelected() {
        setUpExistProjectRequest();
        updateDelegate.updateControls();
    }

    /** {@inheritDoc} */
    @Override
    public void onOpenShiftDescriptionChanged() {
        setUpNewProjectRequest();
    }

    /** {@inheritDoc} */
    @Override
    public void onCheDescriptionChanged() {
        setUpCheProjectRequest();
    }

    /** {@inheritDoc} */
    @Override
    public void onOpenShiftDisplayNameChanged() {
        setUpNewProjectRequest();
    }

    private void setUpNewProjectRequest() {
        ProjectRequest projectRequest;

        if (dataObject.getProjectRequest() == null) {
            dataObject.withProjectRequest(dtoFactory.createDto(ProjectRequest.class));
        }

        projectRequest = dataObject.getProjectRequest();
        projectRequest.withApiVersion("v1")
                      .withDescription(view.getOpenShiftProjectDescription())
                      .withDisplayName(view.getOpenShiftProjectDisplayName())
                      .withMetadata(dtoFactory.createDto(ObjectMeta.class).withName(view.getOpenShiftNewProjectName()));

        if (dataObject.getProject() != null) {
            dataObject.setProject(null);
        }
    }

    private void setUpExistProjectRequest() {
        dataObject.setProject(view.getExistedSelectedProject());

        if (dataObject.getProjectRequest() != null) {
            dataObject.setProjectRequest(null);
        }
    }

    private void setUpCheProjectRequest() {
        ProjectConfigDto projectConfig;

        if (dataObject.getProjectConfigDto() == null) {
            dataObject.withProjectConfigDto(dtoFactory.createDto(ProjectConfigDto.class)
                                                      .withSource(dtoFactory.createDto(SourceStorageDto.class)));
        }

        projectConfig = dataObject.getProjectConfigDto();
        projectConfig.withName(view.getCheNewProjectName())
                     .withDescription(view.getCheProjectDescription());
    }
}

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
package org.eclipse.che.ide.ext.java.client.refactoring.rename.wizard;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.promises.client.Function;
import org.eclipse.che.api.promises.client.FunctionException;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.promises.client.PromiseError;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.editor.EditorAgent;
import org.eclipse.che.ide.api.editor.EditorPartPresenter;
import org.eclipse.che.ide.api.event.FileContentUpdateEvent;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.ext.java.client.JavaLocalizationConstant;
import org.eclipse.che.ide.ext.java.client.project.node.JavaFileNode;
import org.eclipse.che.ide.ext.java.client.project.node.PackageNode;
import org.eclipse.che.ide.ext.java.client.projecttree.JavaSourceFolderUtil;
import org.eclipse.che.ide.ext.java.client.refactoring.RefactorInfo;
import org.eclipse.che.ide.ext.java.client.refactoring.preview.PreviewPresenter;
import org.eclipse.che.ide.ext.java.client.refactoring.rename.wizard.RenameView.ActionDelegate;
import org.eclipse.che.ide.ext.java.client.refactoring.rename.wizard.similarnames.SimilarNamesConfigurationPresenter;
import org.eclipse.che.ide.ext.java.client.refactoring.service.RefactoringServiceClient;
import org.eclipse.che.ide.ext.java.shared.dto.refactoring.ChangeCreationResult;
import org.eclipse.che.ide.ext.java.shared.dto.refactoring.CreateRenameRefactoring;
import org.eclipse.che.ide.ext.java.shared.dto.refactoring.RefactoringResult;
import org.eclipse.che.ide.ext.java.shared.dto.refactoring.RefactoringSession;
import org.eclipse.che.ide.ext.java.shared.dto.refactoring.RefactoringStatus;
import org.eclipse.che.ide.ext.java.shared.dto.refactoring.RenameRefactoringSession;
import org.eclipse.che.ide.ext.java.shared.dto.refactoring.RenameSettings;
import org.eclipse.che.ide.ext.java.shared.dto.refactoring.ValidateNewName;
import org.eclipse.che.ide.jseditor.client.texteditor.TextEditor;
import org.eclipse.che.ide.part.explorer.project.ProjectExplorerPresenter;

import static org.eclipse.che.ide.ext.java.shared.dto.refactoring.CreateRenameRefactoring.RenameType.COMPILATION_UNIT;
import static org.eclipse.che.ide.ext.java.shared.dto.refactoring.CreateRenameRefactoring.RenameType.JAVA_ELEMENT;
import static org.eclipse.che.ide.ext.java.shared.dto.refactoring.CreateRenameRefactoring.RenameType.PACKAGE;
import static org.eclipse.che.ide.ext.java.shared.dto.refactoring.RefactoringStatus.INFO;
import static org.eclipse.che.ide.ext.java.shared.dto.refactoring.RefactoringStatus.OK;

/**
 * The class that manages Move panel widget.
 *
 * @author Valeriy Svydenko
 */
@Singleton
public class RenamePresenter implements ActionDelegate {
    private final RenameView                         view;
    private final SimilarNamesConfigurationPresenter similarNamesConfigurationPresenter;
    private final JavaLocalizationConstant           locale;
    private final EventBus                           eventBus;
    private final ProjectExplorerPresenter           projectExplorer;
    private final EditorAgent                        editorAgent;
    private final NotificationManager                notificationManager;
    private final AppContext                         appContext;
    private final PreviewPresenter                   previewPresenter;
    private final DtoFactory                         dtoFactory;
    private final RefactoringServiceClient           refactorService;

    private RenameRefactoringSession renameRefactoringSession;
    private RefactorInfo             refactorInfo;

    @Inject
    public RenamePresenter(RenameView view,
                           SimilarNamesConfigurationPresenter similarNamesConfigurationPresenter,
                           JavaLocalizationConstant locale,
                           EventBus eventBus,
                           EditorAgent editorAgent,
                           AppContext appContext,
                           ProjectExplorerPresenter projectExplorer,
                           NotificationManager notificationManager,
                           PreviewPresenter previewPresenter,
                           RefactoringServiceClient refactorService,
                           DtoFactory dtoFactory) {
        this.view = view;
        this.similarNamesConfigurationPresenter = similarNamesConfigurationPresenter;
        this.locale = locale;
        this.eventBus = eventBus;
        this.projectExplorer = projectExplorer;
        this.editorAgent = editorAgent;
        this.notificationManager = notificationManager;
        this.view.setDelegate(this);
        this.appContext = appContext;
        this.previewPresenter = previewPresenter;
        this.refactorService = refactorService;
        this.dtoFactory = dtoFactory;
    }

    /**
     * Show Rename window with the special information.
     *
     * @param refactorInfo
     *         information about the rename operation
     */
    public void show(RefactorInfo refactorInfo) {
        this.refactorInfo = refactorInfo;
        final CreateRenameRefactoring createRenameRefactoring = createRenameRefactoringDto(refactorInfo);

        Promise<RenameRefactoringSession> createRenamePromise = refactorService.createRenameRefactoring(createRenameRefactoring);
        createRenamePromise.then(new Operation<RenameRefactoringSession>() {
            @Override
            public void apply(RenameRefactoringSession session) throws OperationException {
                show(session);
            }
        }).catchError(new Operation<PromiseError>() {
            @Override
            public void apply(PromiseError arg) throws OperationException {
                notificationManager.showError(arg.getMessage());
            }
        });
    }

    /**
     * Show Rename window with the special information.
     *
     * @param renameRefactoringSession
     *         data of current refactoring session
     */
    public void show(RenameRefactoringSession renameRefactoringSession) {
        this.renameRefactoringSession = renameRefactoringSession;
        prepareWizard();

        switch (renameRefactoringSession.getWizardType()) {
            case COMPILATION_UNIT:
                view.setTitle(locale.renameCompilationUnitTitle());
                view.setVisiblePatternsPanel(true);
                view.setVisibleFullQualifiedNamePanel(true);
                view.setVisibleSimilarlyVariablesPanel(true);
                break;
            case PACKAGE:
                view.setTitle(locale.renamePackageTitle());
                view.setVisiblePatternsPanel(true);
                view.setVisibleFullQualifiedNamePanel(true);
                view.setVisibleRenameSubpackagesPanel(true);
                break;
            case TYPE:
                view.setTitle(locale.renameTypeTitle());
                view.setVisiblePatternsPanel(true);
                view.setVisibleFullQualifiedNamePanel(true);
                view.setVisibleSimilarlyVariablesPanel(true);
                break;
            case FIELD:
                view.setTitle(locale.renameFieldTitle());
                view.setVisiblePatternsPanel(true);
                break;
            case ENUM_CONSTANT:
                view.setTitle(locale.renameEnumTitle());
                view.setVisiblePatternsPanel(true);
                break;
            case TYPE_PARAMETER:
                view.setTitle(locale.renameTypeVariableTitle());
                break;
            case METHOD:
                view.setTitle(locale.renameMethodTitle());
                view.setVisibleKeepOriginalPanel(true);
                break;
            case LOCAL_VARIABLE:
                view.setTitle(locale.renameLocalVariableTitle());
                break;
            default:
        }

        view.show();
    }

    /** {@inheritDoc} */
    @Override
    public void onPreviewButtonClicked() {
        showPreview();
    }

    /** {@inheritDoc} */
    @Override
    public void onAcceptButtonClicked() {
        applyChanges();
    }

    /** {@inheritDoc} */
    @Override
    public void validateName() {
        ValidateNewName validateNewName = dtoFactory.createDto(ValidateNewName.class);
        validateNewName.setSessionId(renameRefactoringSession.getSessionId());
        validateNewName.setNewName(view.getNewName());

        refactorService.validateNewName(validateNewName).then(new Operation<RefactoringStatus>() {
            @Override
            public void apply(RefactoringStatus arg) throws OperationException {
                switch (arg.getSeverity()) {
                    case OK:
                        view.setEnableAcceptButton(true);
                        view.setEnablePreviewButton(true);
                        view.clearErrorLabel();
                        break;
                    case INFO:
                        view.setEnableAcceptButton(true);
                        view.setEnablePreviewButton(true);
                        view.showStatusMessage(arg);
                        break;
                    default:
                        view.setEnableAcceptButton(false);
                        view.setEnablePreviewButton(false);
                        view.showErrorMessage(arg);
                        break;
                }
            }
        }).catchError(new Operation<PromiseError>() {
            @Override
            public void apply(PromiseError arg) throws OperationException {
                notificationManager.showError(arg.getMessage());
            }
        });
    }

    private void prepareWizard() {
        view.clearErrorLabel();
        view.setOldName(renameRefactoringSession.getOldName());
        view.setVisiblePatternsPanel(false);
        view.setVisibleFullQualifiedNamePanel(false);
        view.setVisibleKeepOriginalPanel(false);
        view.setVisibleRenameSubpackagesPanel(false);
        view.setVisibleSimilarlyVariablesPanel(false);
        view.setEnableAcceptButton(false);
        view.setEnablePreviewButton(false);
    }

    private void showPreview() {
        RefactoringSession session = dtoFactory.createDto(RefactoringSession.class);
        session.setSessionId(renameRefactoringSession.getSessionId());

        prepareRenameChanges(session).then(new Operation<ChangeCreationResult>() {
            @Override
            public void apply(ChangeCreationResult arg) throws OperationException {
                if (arg.isCanShowPreviewPage()) {
                    previewPresenter.show(renameRefactoringSession.getSessionId(), refactorInfo);
                    previewPresenter.setTitle(locale.renameItemTitle());
                    view.hide();
                } else {
                    view.showErrorMessage(arg.getStatus());
                }
            }
        }).catchError(new Operation<PromiseError>() {
            @Override
            public void apply(PromiseError arg) throws OperationException {
                notificationManager.showError(arg.getMessage());
            }
        });
    }

    private void applyChanges() {
        final RefactoringSession session = dtoFactory.createDto(RefactoringSession.class);
        session.setSessionId(renameRefactoringSession.getSessionId());

        prepareRenameChanges(session).then(new Operation<ChangeCreationResult>() {
            @Override
            public void apply(ChangeCreationResult arg) throws OperationException {
                if (!arg.isCanShowPreviewPage()) {
                    view.showErrorMessage(arg.getStatus());
                    return;
                }

                refactorService.applyRefactoring(session).then(new Operation<RefactoringResult>() {
                    @Override
                    public void apply(RefactoringResult arg) throws OperationException {
                        if (arg.getSeverity() == OK) {
                            view.hide();
                            //TODO It is temporary solution. We need to know which files have changes.
                            projectExplorer.reloadChildren();
                            updateAllEditors();
                        } else {
                            view.showErrorMessage(arg);
                        }
                    }
                });

            }
        }).catchError(new Operation<PromiseError>() {
            @Override
            public void apply(PromiseError arg) throws OperationException {
                notificationManager.showError(arg.getMessage());
            }
        });
    }

    private Promise<ChangeCreationResult> prepareRenameChanges(final RefactoringSession session) {
        RenameSettings renameSettings = createRenameSettingsDto(session);

        return refactorService.setRenameSettings(renameSettings).thenPromise(new Function<Void, Promise<ChangeCreationResult>>() {
            @Override
            public Promise<ChangeCreationResult> apply(Void arg) throws FunctionException {
                return refactorService.createChange(session);
            }
        });
    }

    private RenameSettings createRenameSettingsDto(RefactoringSession session) {
        RenameSettings renameSettings = dtoFactory.createDto(RenameSettings.class);
        renameSettings.setSessionId(session.getSessionId());
        renameSettings.setDelegateUpdating(view.isUpdateDelegateUpdating());
        if (view.isUpdateDelegateUpdating()) {
            renameSettings.setDeprecateDelegates(view.isUpdateMarkDeprecated());
        }
        renameSettings.setUpdateSubpackages(view.isUpdateSubpackages());
        renameSettings.setUpdateReferences(view.isUpdateReferences());
        renameSettings.setUpdateQualifiedNames(view.isUpdateQualifiedNames());
        if (view.isUpdateQualifiedNames()) {
            renameSettings.setFilePatterns(view.getFilePatterns());
        }
        renameSettings.setUpdateTextualMatches(view.isUpdateTextualOccurrences());
        renameSettings.setUpdateSimilarDeclarations(view.isUpdateSimilarlyVariables());
        if (view.isUpdateSimilarlyVariables()) {
            renameSettings.setMachStrategy(similarNamesConfigurationPresenter.getMachStrategy().getValue());
        }

        return renameSettings;
    }

    private CreateRenameRefactoring createRenameRefactoringDto(RefactorInfo refactorInfo) {
        CreateRenameRefactoring dto = dtoFactory.createDto(CreateRenameRefactoring.class);

        dto.setRefactorLightweight(false);

        if (refactorInfo == null) {
            dto.setType(JAVA_ELEMENT);
            dto.setPath(JavaSourceFolderUtil.getFQNForFile(editorAgent.getActiveEditor().getEditorInput().getFile()));
            dto.setOffset(((TextEditor)editorAgent.getActiveEditor()).getCursorOffset());
        } else {
            Object selectedElement = refactorInfo.getSelectedItems().get(0);
            if (selectedElement instanceof JavaFileNode) {
                dto.setPath(JavaSourceFolderUtil.getFQNForFile((JavaFileNode)selectedElement));
                dto.setType(COMPILATION_UNIT);
            } else if (selectedElement instanceof PackageNode) {
                dto.setPath(((PackageNode)selectedElement).getStorablePath());
                dto.setType(PACKAGE);
            }
        }

        String projectPath = appContext.getCurrentProject().getProjectDescription().getPath();
        dto.setProjectPath(projectPath);

        return dto;
    }

    private void updateAllEditors() {
        for (EditorPartPresenter editor : editorAgent.getOpenedEditors().values()) {
            String path = editor.getEditorInput().getFile().getPath();
            eventBus.fireEvent(new FileContentUpdateEvent(path));
        }
    }

}
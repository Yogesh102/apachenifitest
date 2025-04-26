import { Component, OnDestroy, OnInit } from "@angular/core";
import { ActivatedRoute } from "@angular/router";
import { Node, NodeEntry, NodeAssociationEntry,NodesApi, Version, VersionPaging, VersionsApi } from "@alfresco/js-api";
import { AlfrescoApiService, NotificationService, AuthenticationService, AppConfigService } from "@alfresco/adf-core";
import { takeUntil } from "rxjs/operators";
import { Subject } from "rxjs";
import { Location } from "@angular/common";
import { ToolbarComponent } from "libs/content-ee/content-services-extension/src/lib/components/shared";
import { Store } from "@ngrx/store";
import { AppExtensionService } from "@alfresco/aca-shared";
import { SetCurrentFolderAction, SetSelectedNodesAction, getAppSelection } from "@alfresco/aca-shared/store";
import { DeleteVersionAction, DownloadVersionAction, RELOAD_VERSIONS, ReloadVersionsAction, RestoreVersionAction, VIEW_FILE_VERSION, ViewVersionAction } from "../../store/delta.actions";
import { Actions, ofType } from "@ngrx/effects";
//import { MatDialog } from '@angular/material/dialog';
//import { ErrorDialogComponent } from '../error-dialog/error-dialog.component';


const BLOCKED_FILE_TYPE = [
    "application/zip",
];

const MAX_VIEWABLE_FILE_SIZE = 200000; // in bytes

@Component({
    templateUrl: './file-versions.component.html',
    styleUrls: ['./file-versions.component.scss'],
})
export class FileVersionsComponent extends ToolbarComponent implements OnInit, OnDestroy {

    private performAction$ = new Subject();

    nodeId = '';
    versionId = '';
    version: Node | undefined;
    isLoading = false;
    // pagination
    sizes: number[] = [25, 50, 100];

    // holds actual versions list
    versions: VersionPaging | undefined;

    // allowed operations
    private canDelete = false;
    private canUpdate = false;


    private _versionsApi: VersionsApi | undefined;
    get versionsApi(): VersionsApi {
        this._versionsApi = this._versionsApi ?? new VersionsApi(this.alfrescoApi.getInstance());
        return this._versionsApi;
    }

    private _nodesApi: NodesApi | undefined;
    get nodesApi(): NodesApi {
        this._nodesApi = this._nodesApi ?? new NodesApi(this.alfrescoApi.getInstance());
        return this._nodesApi;
    }

    formatRevision(id: string): string {
        return id.substring(0, id.indexOf("."));
    }

    constructor(
        protected override store: Store<any>,
        protected override appExtensionService: AppExtensionService,
        private actions$: Actions,
        private alfrescoApi: AlfrescoApiService,
        private authService: AuthenticationService,
        private appConfig: AppConfigService,
        private route: ActivatedRoute,
        private _location: Location,
        private notifications: NotificationService
    ) {
        super(store, appExtensionService);
    }

    override ngOnInit(): void {
        this.store
            .select(getAppSelection)
            .pipe(takeUntil(this.onDestroy$))
            .subscribe((selection) => {
                this.selection = selection;
            });

        this.appExtensionService.getAllowedToolbarActions()
            .pipe(takeUntil(this.onDestroy$))
            .subscribe((actions) => {
                this.actions = actions.filter(action => action.id.startsWith('delta.files.version.toolbar'));
            });

        // reload version list on updates
        this.actions$.pipe(ofType<ReloadVersionsAction>(RELOAD_VERSIONS), takeUntil(this.onDestroy$))
            .subscribe(() => {
                this.store.dispatch(new SetSelectedNodesAction([]));
                this.loadVersions({});
            });

        this.actions$.pipe(ofType<ViewVersionAction>(VIEW_FILE_VERSION), takeUntil(this.onDestroy$))
            .subscribe(() => {
                this.viewVersion(new CustomEvent('view-version', {
                    detail: { node: this.selection.file }
                }));
            })

        this.isLoading = true;
        this.route.params.subscribe((params) => {
            this.nodeId = params['nodeId'];
            if (this.nodeId) {
                this.nodesApi.getNode(this.nodeId, {
                    include: ["permissions", "allowableOperations"]
                }).then((node: NodeEntry) => {
                    this.store.dispatch(new SetCurrentFolderAction(node.entry));
                    this.canDelete = (node.entry?.allowableOperations ?? []).indexOf('delete') > -1;
                    this.canUpdate = (node.entry?.allowableOperations ?? []).indexOf('update') > -1;
                });
                this.loadVersions({});
            } else {
                console.error("nodeId missing in URL");
            }
        });

        this.performAction$.subscribe((action: any) => this.onExecuteRowAction(action));
    }

    override ngOnDestroy(): void {
        this.onDestroy$.next();
        this.onDestroy$.complete();
        this.performAction$.complete();
    }

    onShowRowActionsMenu(event: any) {
        event.value.actions = [];
        if (!BLOCKED_FILE_TYPE.includes(event.value.row['obj'].entry?.content?.mimeType)) {
            event.value.actions.push({
                data: event.value.row['obj'],
                model: {
                    id: 'view',
                    title: 'View',
                    icon: 'visibility',
                    visible: true,
                },
                subject: this.performAction$,
            });
        }
        event.value.actions.push({
            data: event.value.row['obj'],
            model: {
                id: 'download',
                title: 'Download',
                icon: 'download',
                visible: true,
            },
            subject: this.performAction$,
        });
        event.value.actions.push({
            data: event.value.row['obj'],
            model: {
                id: 'revert',
                title: 'Restore',
                icon: 'history',
                visible: this.canUpdate,
            },
            subject: this.performAction$,
        });
        event.value.actions.push({
            data: event.value.row['obj'],
            model: {
                id: 'delete',
                title: 'Delete',
                icon: 'delete',
                visible: this.canDelete,
            },
            subject: this.performAction$,
        });
    }

    onExecuteRowAction(event: any) {
        const actionId = event.model.id;

        switch (actionId) {
            case 'view':
                this.viewVersion(new CustomEvent('view-version', {
                    detail: { node: event.data }
                }));
                break;
            case 'download':
                this.store.dispatch(new DownloadVersionAction(event.data.entry));
                break;
            case 'revert':
                this.store.dispatch(new RestoreVersionAction(event.data.entry, { majorVersion: true, comment: '' }));
                break;
            case 'delete':
                this.store.dispatch(new DeleteVersionAction(event.data.entry))
                break;
        }

    }

    loadVersions({ skipCount = 0, maxItems = 100 }) {
        this.isLoading = true;
        this.versionsApi.listVersionHistory(this.nodeId, {
            skipCount: skipCount,
            maxItems: maxItems,
            include: [
                "properties",
                "permissions",
            ]
        })
            .then((value: VersionPaging) => this.versions = value)
            .finally(() => this.isLoading = false);
    }

    viewVersion(viewVersion: Event) {
        if (!(viewVersion instanceof CustomEvent)) {
            return;
        }

        const versionMetadata = viewVersion.detail.node;
        //const versionLabel = versionMetadata?.entry?.id;

        if (versionMetadata?.entry?.content?.sizeInBytes >= MAX_VIEWABLE_FILE_SIZE) {

            const nodeRef = `workspace://SpacesStore/${this.nodeId}`;
          
            this.isNodeInAllowedFolder(this.nodeId).then((isAllowed) => {
                if (isAllowed) {
                    
                        this.alfrescoApi.getInstance().contentClient.callCustomApi(
                            `/alfresco/service/api/version?nodeRef=${encodeURIComponent(nodeRef)}`,
                            'GET',
                            {}, {}, {}, {}, null, ['application/json'], ['application/json']
                        ).then((response: any) => {
                            const versionLabel = versionMetadata?.entry?.id;
                            const match = response?.find((v: any) => v.label === versionLabel);
        
                            if (match?.nodeRef) {
                                const versionNodeId = match.nodeRef.replace(/^.*\//, ''); // extract UUID from nodeRef
                                this.openAEVViewer(versionNodeId);
                                return;
                            } else {
                                this.notifications.showError('Version node not found.');
                            }
                        })
                            .catch((error) => {
                                console.log('❌ Error fetching version node from custom API:', error);
                                this.notifications.showError('Failed to load version information.');
                            });
                        return;
                    
                } else {
                  this.notifications.showError('This document is not in an AEV-enabled folder.');
                  this.versionsApi.getVersion(this.nodeId, versionMetadata?.entry?.id)
                  .then(version => {
                      this.versionId = versionMetadata?.entry?.id;
                      this.version = this.convertToNode(version.entry!);
                  });
                }
              });
           return;
        }
        
        if (BLOCKED_FILE_TYPE.includes(versionMetadata?.entry?.content?.mimeType)) {
            this.notifications.showError('DELTA.FILE_VERSION.VIEW_UNSUPPORTED_ERROR');
            return;
        }
    }

    isNodeInAllowedFolder(nodeId: string): Promise<boolean> {
        const allowedFolders: string[] = this.appConfig.get('aev.allowedFolders') || [];
        console.log("Configured AEV Folders" + allowedFolders);
        let parentPaths: string[] = [];

        return this.nodesApi.listParents(nodeId, { include: ['path'] })
            .then((parentsResponse) => {

                let entries: NodeAssociationEntry[];
                entries = parentsResponse.list?.entries ?? [];
                parentPaths =entries.map((entry: any) => entry.entry?.path?.name || '').filter((path) => !!path);
    
                return parentPaths.some(parentPath =>
                    allowedFolders.some(allowed => parentPath.includes(allowed))
                );
            })

            .catch((error) => {
                console.error('Error retrieving parent folders:', error);
                return false;
            });
    }


    /**
   * Trigger the automatic redirection to AEV viewer
   * @param nodeId - Alfresco Node ID
   */
    openAEVViewer(nodeId: string): void {
        const username = this.authService.getEcmUsername() ?? '';
        const ecmTicket = this.authService.getTicketEcm() ?? '';

        const viewerUrl = this.generateAEVLink(ecmTicket, nodeId, username);

        // Open the URL directly using window.open()
        window.open(viewerUrl, '_blank');
    }
    generateAEVLink(ticket: string, nodeId: string, username: string): string {
        const baseUrl = this.appConfig.get('ecmHost') + '/OpenAnnotate/login/external.htm';
        const nodeRef = `workspace://version2Store/${nodeId}`;
        const params = new URLSearchParams({
            username: username,
            mode: 'readOnly',
            ticket: ticket,
            docId: nodeRef
        }).toString();

        return `${baseUrl}?${params.toString()}`;
    }

    goBack() {
        this._location.back();
    }

    onShowViewerChange($event: boolean) {
        this.versionId = '';
        this.version = undefined;
    }

    convertToNode(version: Version): Node {
        return {
            id: version.id,
            name: version.name,
            nodeType: version.nodeType,
            isFolder: false,
            isFile: true,
            modifiedAt: version.modifiedAt,
            modifiedByUser: version.modifiedByUser,
            createdAt: version.modifiedAt,
            createdByUser: version.modifiedByUser,
            content: version.content,
            aspectNames: version.aspectNames,
            properties: version.properties,
        };
    }

    setSelectedRows(event: Event) {
        if ((<CustomEvent>event).detail) {
            let nodes = (<CustomEvent>event).detail.selection.map((row: any) => row['obj']);
            this.store.dispatch(new SetSelectedNodesAction(nodes));
        }
    }

}

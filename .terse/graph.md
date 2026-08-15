# Terse Knowledge Graph — hr-web
# 284 symbols · 175 edges · 23 clusters · 39 files
# Read this instead of grepping. Line = path › Name(kind) Cn → edges. `?`=inferred. Cn=cluster id.

## Clusters
C0 enhanceCoreComponents: allowedCommands, comparableValue, currentRouteId, dispatchNative, enhanceBatchBars, enhanceCollapses  (entry: enhanceCoreComponents)
C1 draw: closePalette, doAction, draw, getItems, go, renderPalette  (entry: draw)
C2 aiPost: aiPost, delay, isTransientGatewayError, runCommunicationDraft, runInterviewQuestions, runJdGenerate  (entry: aiPost)
C3 qs: fetchChannel, fetchDeptProgress, fetchFunnel, fetchKpi, fetchMonthlyStats, fetchRiskAlerts  (entry: qs)
C4 index.js: ai.js, auth.js, config.js, demand.js, health.js, index.js  (entry: index.js)
C5 AiTabBase.js: useClipboard.js, useConfirmDialog.js, useOnline.js, useProcessingSteps.js, useStreaming.js, AiTabBase.js  (entry: vue)
C6 request: doFetch, getCacheKey, getCached, request, setCache, ok  (entry: request)
C7 hire-chain.spec.js: ok, playwright.config.js, screenshot-funnel.mjs, shot.mjs, hire-chain.spec.js  (entry: @playwright/test)
C8 capture-readme-shots.mjs: shot, capture-readme-shots.mjs  (entry: capture-readme-shots.mjs)
C9 mockCandidate: calcDirectScore, calcRecommendScore, getDecayCoefficient, mockCandidate, mockEmployee, pick  (entry: mockCandidate)
C10 openGlobalScheduleModal: addGlobalRound, closeGlobalScheduleModal, openGlobalScheduleModal, renderDepartmentOptions, submitGlobalSchedule  (entry: openGlobalScheduleModal)
C11 _stopTimer: _stopTimer, finish, reset, start  (entry: _stopTimer)
C12 legacy-flow.spec.js: ok, routeCoreApis, seedAuth, legacy-flow.spec.js  (entry: legacy-flow.spec.js)
C13 vite.config.js: vite.config.js  (entry: vite.config.js)
C14 closeContactModal: closeContactModal, openContactModal, openInternalContactModal  (entry: closeContactModal)
C15 closeRematchModal: closeRematchModal, openRematchModal, submitRematch  (entry: closeRematchModal)
C16 installCommandTrigger: enhanceWorkbenchShell, ensureTopbarActions, installCommandTrigger  (entry: installCommandTrigger)
C17 notifyLoadingChange: decrementLoading, incrementLoading, notifyLoadingChange  (entry: notifyLoadingChange)
C18 invalidateCache: invalidateAfterMutation, invalidateCache, uploadResumeFile  (entry: invalidateCache)
C19 settle: onConfirmDialogCancel, onConfirmDialogConfirm, settle  (entry: settle)
C20 getDecayCoefficient: calcRecommendScore, getDecayCoefficient  (entry: getDecayCoefficient)
C21 handleResponse: dispatchApiError, handleResponse  (entry: handleResponse)
C22 useAiTab: useClipboard, useAiTab  (entry: useAiTab)

## Symbols

# frontend/playwright.config.js
playwright.config.js(mod) C7 → imp:@playwright/test?

# frontend/public/js/app.js
app.js(mod) C1 → calls:renderPalette,closePalette,installCommandTrigger
getDecayCoefficient(fn) C20
calcRecommendScore(fn) C20 → calls:getDecayCoefficient
calcDirectScore(fn)
profileColor(fn)
profileGradeLabel(fn)
matchStaleDays(fn)
matchStaleHint(fn)
renderDepartmentOptions(fn) C10
getRole(fn) C0
getUser(fn) C0
getVisibleMenus(fn) C0
renderSidebar(fn) C0 → calls:getRole,getVisibleMenus,getUser
openCandidateDrawer(fn)
closeCandidateDrawer(fn)
openGlobalScheduleModal(fn) C10 → calls:closeGlobalScheduleModal,addGlobalRound
closeGlobalScheduleModal(fn) C10
toggleGsContact(fn)
addGlobalRound(fn) C10 → calls:renderDepartmentOptions
gsRenumber(fn)
gsPickerFilter(fn)
submitGlobalSchedule(fn) C10 → calls:closeGlobalScheduleModal
mockCandidate(fn)
openEmployeeDrawer(fn)
mockEmployee(fn)
openRematchModal(fn) C15 → calls:closeRematchModal
closeRematchModal(fn) C15
submitRematch(fn) C15 → calls:closeRematchModal
renderActionButtons(fn)
openContactModal(fn) C14 → calls:closeContactModal
closeContactModal(fn) C14
openInternalContactModal(fn) C14 → calls:closeContactModal
go(fn) C1
currentRouteId(fn) C0
allowedCommands(fn) C0 → calls:getVisibleMenus,getRole
ensureTopbarActions(fn) C16
enhanceWorkbenchShell(fn) C16 → calls:currentRouteId,ensureTopbarActions
textOf(fn) C0
dispatchNative(fn) C0
enhanceMetricCards(fn) C0 → calls:textOf
enhanceStatusLabels(fn) C0 → calls:textOf
enhanceFilterBars(fn) C0 → calls:textOf,dispatchNative
comparableValue(fn) C0
sortTable(fn) C0 → calls:comparableValue,textOf
restoreTableSortState(fn) C0 → calls:currentRouteId,sortTable
wrapRenderFunctions(fn) C0 → calls:restoreTableSortState
enhanceTables(fn) C0 → calls:textOf,currentRouteId,toggleSort,restoreTableSortState,wrapRenderFunctions
toggleSort(fn) C0 → calls:sortTable,currentRouteId
enhanceBatchBars(fn) C0
enhanceDialogs(fn) C0 → calls:textOf
enhanceEmptyStates(fn) C0 → calls:textOf
enhanceVisualizationCards(fn) C0 → calls:textOf
ensureHeroModuleTabs(fn) C0 → calls:getVisibleMenus,getRole,currentRouteId
ensureHeroDashboardAnalytics(fn) C0 → calls:currentRouteId
ensureHeroDashboardMaterial(fn) C0 → calls:currentRouteId,refineDashboardLegacyCards
refineDashboardLegacyCards(fn) C0 → calls:currentRouteId
ensureHeroOperationalWorkspace(fn) C0 → calls:currentRouteId
enhanceKineticTypography(fn) C0
enhanceScrollReveal(fn) C0
ensureHeroPageSummary(fn) C0 → calls:currentRouteId
enhanceCollapses(fn) C0 → calls:setOpen
setOpen(fn) C0
setOpen(fn)
enhanceCoreComponents(fn) C0 → calls:ensureHeroPageSummary,ensureHeroOperationalWorkspace,enhanceMobileShell,enhanceMetricCards,enhanceStatusLabels,enhanceFilterBars,enhanceTables,enhanceBatchBars,enhanceDialogs,enhanceEmptyStates,enhanceVisualizationCards,enhanceCollapses,enhanceKineticTypography,enhanceScrollReveal
scheduleCoreEnhancements(fn)
closePalette(fn) C1
enhanceMobileShell(fn) C0 → calls:currentRouteId,getVisibleMenus,getRole,setOpen
setOpen(fn)
renderPalette(fn) C1 → calls:closePalette,updateSelection,draw
getItems(fn) C1 → calls:allowedCommands
draw(fn) C1 → calls:getItems,trackPaletteHistory,go,doAction,closePalette,updateSelection
updateSelection(fn) C1
trackPaletteHistory(fn) C1
doAction(fn) C1
installCommandTrigger(fn) C16 → calls:enhanceWorkbenchShell,enhanceCoreComponents,ensureTopbarActions

# frontend/scripts/capture-readme-shots.mjs
capture-readme-shots.mjs(mod) C8 → calls:shot imp:playwright?,node:fs?,node:path?,node:url?
shot(fn) C8

# frontend/scripts/screenshot-funnel.mjs
screenshot-funnel.mjs(mod) C7 → imp:@playwright/test?
initAuth(fn)

# frontend/scripts/shot.mjs
shot.mjs(mod) C7 → imp:@playwright/test?

# frontend/src/api/ai.js
ai.js(mod) C4 → imp:index.js
delay(fn) C2
isTransientGatewayError(fn) C2
aiPost(fn) C2 → calls:isTransientGatewayError,delay
runJdGenerate(fn) C2 → calls:aiPost
runResumeSearch(fn) C2 → calls:aiPost
runMatch(fn) C2 → calls:aiPost
runInterviewQuestions(fn) C2 → calls:aiPost
runCommunicationDraft(fn) C2 → calls:aiPost
runReportAnalysis(fn) C2 → calls:aiPost

# frontend/src/api/auth.js
auth.js(mod) C4 → imp:index.js
fetchMe(fn)
login(fn)
logout(fn)
fetchUsers(fn)
createUser(fn)
updateUser(fn)
toggleUserStatus(fn)
deleteUser(fn)
resetUserPassword(fn)
fetchPositions(fn)
changePassword(fn)
forgotPassword(fn)
verifyResetCode(fn)
batchCreateUsers(fn)
firstTimeSetup(fn)
createDepartment(fn)
updateDepartment(fn)
deleteDepartment(fn)
toggleDepartmentStatus(fn)
createPosition(fn)
fetchPendingAccounts(fn)
updatePosition(fn)
deletePosition(fn)
togglePositionStatus(fn)

# frontend/src/api/config.js
config.js(mod) C4 → imp:index.js
fetchEmailAccounts(fn)
resolveEmailServer(fn)
createEmailAccount(fn)
updateEmailAccount(fn)
deleteEmailAccount(fn)
syncAllEmailAccounts(fn)
syncEmailAccount(fn)
fetchChannels(fn)
createChannel(fn)
updateChannel(fn)
fetchScoreRules(fn)
updateScoreRules(fn)
fetchNotifyTemplates(fn)
createNotifyTemplate(fn)
updateNotifyTemplate(fn)
deleteNotifyTemplate(fn)
fetchKnowledgeBase(fn)
updateKnowledgeBase(fn)
fetchRolePermissions(fn)
updateRolePermissions(fn)
fetchAuditLogs(fn)
fetchAiCapabilities(fn)
fetchApiKeys(fn)
saveApiKeys(fn)
testApiKey(fn)
fetchTencentStatus(fn)
fetchFeishuStatus(fn)
fetchDepartments(fn)

# frontend/src/api/dashboard.js
dashboard.js(mod) C0 → imp:index.js
qs(fn) C3
fetchKpi(fn) C3 → calls:qs
fetchFunnel(fn) C3 → calls:qs
fetchDeptProgress(fn) C3 → calls:qs
fetchChannel(fn) C3 → calls:qs
fetchRiskAlerts(fn) C3 → calls:qs
fetchMonthlyStats(fn) C3 → calls:qs

# frontend/src/api/demand.js
demand.js(mod) C4 → imp:index.js
fetchDemands(fn)
submitForApproval(fn)
approveDemandApi(fn)
rejectDemandApi(fn)
fetchDemandDetail(fn)
fetchDemandCandidates(fn)
createDemand(fn)
updateDemand(fn)
deleteDemand(fn)
linkCandidateToDemand(fn)

# frontend/src/api/health.js
health.js(mod) C4 → imp:index.js
fetchHealth(fn)

# frontend/src/api/index.js
index.js(mod) C4 → calls:request
notifyLoadingChange(fn) C17
onLoadingChange(fn)
isLoading(fn)
incrementLoading(fn) C17 → calls:notifyLoadingChange
decrementLoading(fn) C17 → calls:notifyLoadingChange
getCacheKey(fn) C6
getCached(fn) C6
setCache(fn) C6
invalidateAfterMutation(fn) C18 → calls:invalidateCache
invalidateCache(fn) C18
clearCache(fn)
request(fn) C6 → calls:getCacheKey,getCached,incrementLoading,doFetch,handleResponse,invalidateAfterMutation,setCache,decrementLoading
doFetch(fn) C6
handleResponse(fn) C21 → calls:dispatchApiError
dispatchApiError(fn) C21

# frontend/src/api/interview.js
interview.js(mod) C4 → imp:index.js
fetchInterviews(fn)
fetchInterviewAlerts(fn)
createInterview(fn)
scheduleInterview(fn)
completeInterview(fn)
evaluateInterview(fn)
sendOffer(fn)
confirmOnboard(fn)
cancelInterview(fn)
fetchInterviewDetail(fn)
fetchInterviewCalendar(fn)

# frontend/src/api/talent.js
talent.js(mod) C4 → imp:index.js
fetchTalent(fn)
updateTalentNote(fn)
fetchMatchResults(fn)
fetchCandidateDetail(fn)
fetchEmployeeDetail(fn)
linkTalentToDemand(fn)
recordTalentContact(fn)
fetchCandidateContact(fn)
sendTalentContact(fn)
fetchIngestLog(fn)
fetchMailLog(fn)
batchContactCandidates(fn)
uploadResumeFile(fn) C18 → calls:invalidateCache?
fetchResumeFile(fn)

# frontend/src/components/kpiIcons.js
kpiIcons.js(mod)
resolveKpiIcon(fn)

# frontend/src/composables/useAppError.js
useAppError.js(mod) C0 → imp:useToast.js,useAuth.js
useAppError(fn) C0 → calls:useToast?
handleError(fn)

# frontend/src/composables/useAuth.js
useAuth.js(mod) C0
getRoleLanding(fn) C0 → calls:getRole
setAuth(fn)
getRole(fn) C0
getUser(fn)
getVisibleMenus(fn)
clearAuth(fn)

# frontend/src/composables/useClipboard.js
useClipboard.js(mod) C5 → imp:vue?
useClipboard(fn) C22
copy(fn)

# frontend/src/composables/useConfirmDialog.js
useConfirmDialog.js(mod) C5 → imp:vue?
useConfirmDialog(fn)
askConfirm(fn)
settle(fn) C19
onConfirmDialogConfirm(fn) C19 → calls:settle
onConfirmDialogCancel(fn) C19 → calls:settle

# frontend/src/composables/useMockData.js
useMockData.js(mod)
renderDepartmentOptions(fn)
getDecayCoefficient(fn) C9
calcRecommendScore(fn) C9 → calls:getDecayCoefficient
calcDirectScore(fn) C9
profileColor(fn)
profileGradeLabel(fn)
pick(fn) C9
mockCandidate(fn) C9 → calls:pick,calcRecommendScore,calcDirectScore
mockEmployee(fn) C9 → calls:pick

# frontend/src/composables/useOnline.js
useOnline.js(mod) C5 → imp:vue?
useOnline(fn)
onOnline(fn)
onOffline(fn)

# frontend/src/composables/useProcessingSteps.js
useProcessingSteps.js(mod) C5 → imp:vue?
useProcessingSteps(fn)
_stopTimer(fn) C11
start(fn) C11 → calls:_stopTimer
finish(fn) C11 → calls:_stopTimer
reset(fn) C11 → calls:_stopTimer

# frontend/src/composables/useStreaming.js
useStreaming.js(mod) C5 → imp:vue?
useStreaming(fn)
start(fn)
stop(fn)

# frontend/src/composables/useToast.js
useToast.js(mod) C0 → calls:addToast imp:vue?
addToast(fn) C0
useToast(fn) C0 → calls:addToast
removeToast(fn)

# frontend/src/data/ai.js
ai.js(mod)

# frontend/src/data/config.js
config.js(mod)

# frontend/src/data/dashboard.js
dashboard.js(mod)

# frontend/src/data/demand-detail.js
demand-detail.js(mod)

# frontend/src/data/demand.js
demand.js(mod)
getLinkedCount(fn)

# frontend/src/data/interview.js
interview.js(mod)

# frontend/src/data/talent.js
talent.js(mod)

# frontend/src/main.js
main.js(mod) C0 → calls:prefetchWorkbenchData? imp:vue?,index.js,dataPrefetch.js

# frontend/src/router/index.js
index.js(mod) C0 → calls:getRoleLanding?,checkAuth imp:vue-router?,useAuth.js
checkAuth(fn) C0 → calls:getRole?,getRoleLanding?

# frontend/src/services/dataPrefetch.js
dataPrefetch.js(mod) C0 → imp:dashboard.js
prefetchWorkbenchData(fn) C0 → calls:run
run(fn) C3 → calls:fetchKpi?,fetchFunnel?

# frontend/src/views/ai/AiTabBase.js
AiTabBase.js(mod) C5 → imp:vue?,useClipboard.js
useAiTab(fn) C22 → calls:useClipboard?
resetError(fn)

# frontend/tests/hire-chain.spec.js
hire-chain.spec.js(mod) C7 → calls:ok imp:@playwright/test?
ok(fn) C7

# frontend/tests/legacy-flow.spec.js
legacy-flow.spec.js(mod) C12 → calls:seedAuth,routeCoreApis,request?,ok imp:@playwright/test?
ok(fn) C12
seedAuth(fn) C12
routeCoreApis(fn) C12 → calls:ok

# frontend/tests/regression-20260723.spec.js
regression-20260723.spec.js(mod) C6 → calls:ok,request? imp:@playwright/test?
ok(fn) C6

# frontend/vite.config.js
vite.config.js(mod) C13 → imp:vite?,@vitejs/plugin-vue?

## Hubs (most connected)
enhanceCoreComponents(15) · currentRouteId(11) · request(11) · index.js(9) · textOf(8) · aiPost(8) · draw(7) · enhanceTables(6) · qs(6) · getRole(5) · enhanceMobileShell(5) · capture-readme-shots.mjs(5)

var FormEditStatusController = function(status, $scope, $controller, $uibModal, $anchorScroll, locationUtils, statusService) {

    // extends the FormStatusController to inherit common methods
    angular.extend(this, $controller('FormStatusController', { status: status, $scope: $scope }));

    var deleteStatus = function(status) {
        statusService.deleteStatus(status.id)
            .then(function() {
                locationUtils.navigateToPath('/admin/statuses');
            });
    };

    $scope.statusName = angular.copy(status.name);

    $scope.settings = {
        showDelete: true,
        saveLabel: 'Update'
    };

    $scope.save = function(status) {
        statusService.updateStatus(status).
            then(function() {
                $scope.statusName = angular.copy(status.name);
                $anchorScroll(); // scrolls window to top
            });
    };

    $scope.confirmDelete = function(status) {
        var params = {
            title: 'Confirm Delete',
            message: 'This action CANNOT be undone. This will permanently delete ' + status.name + '. Are you sure you want to delete ' + status.name + '?'
        };
        var modalInstance = $uibModal.open({
            templateUrl: 'common/modules/dialog/confirm/dialog.confirm.tpl.html',
            controller: 'DialogConfirmController',
            size: 'md',
            resolve: {
                params: function () {
                    return params;
                }
            }
        });
        modalInstance.result.then(function() {
            deleteStatus(status);
        }, function () {
            // do nothing
        });
    };

};

FormEditStatusController.$inject = ['status', '$scope', '$controller', '$uibModal', '$anchorScroll', 'locationUtils', 'statusService'];
module.exports = FormEditStatusController;
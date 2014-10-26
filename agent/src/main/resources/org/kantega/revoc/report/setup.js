angular.module("setup", [])
    .controller("SetupController", function ($scope, $http, $window) {

        $scope.packages = "com.example";
        $scope.port = 7070;
        $scope.mavenRepo = "http://central.maven.org/maven2";

        $http.get("agent-file")
            .success(function(data) {
                $scope.agentFile = data;
            });

        $http.get("maven-repo")
            .success(function(data) {
                $scope.mavenDownloadRepo = data;
            });

        $scope.agentLine = function() {
            if($scope.agentFile) {
                return "-javaagent:" + $scope.agentFile +"=packages=" + $scope.packages +",port=" +$scope.port;
            }
        }

        $scope.env= function() {
            var env = "";
            if($scope.mavenRepo) {
                env +="export REVOC_MAVEN_REPO=" + $scope.mavenRepo +"\n";
                if($scope.mavenDownloadRepo) {
                    env +="export REVOC_MAVEN_DOWNLOAD=" + $scope.mavenDownloadRepo +"\n";
                }
            }

            return env;
        }

        $window.onbeforeunload = function() {
            $http.post("finished");
        }
    });
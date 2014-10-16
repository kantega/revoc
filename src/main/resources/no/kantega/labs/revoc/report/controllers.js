angular.module("revoc")
    .controller("RevocController", ["$scope", "$http", "websocket", "classesParser",

        function ($scope, $http, websocket, classesParser) {


            function newData(data) {
                $scope.$apply(function () {
                    $scope.data = data;
                    $scope.classes = classesParser(data.classes);
                    $scope.loaders = parseLoaders(data.loaders);
                    if($scope.selectedSource) {
                        updateSourceLines($scope.selectedSource.lines, $scope.data.classes[$scope.selectedSource.class.id]);
                    }
                })

                console.log("New data: " + $scope.data)
            }

            function parseLoaders(loaders) {
                var l = [];
                for (var i = 0; i < loaders.length; i+=2) {
                    l.push({id: loaders[i], name: loaders[i+1]});

                }
                return l;
            }

            function updateSourceLines(lines, data) {

                var tref = data[3];

                for (var i = 0; i < data[1].length; i++) {
                    var lineNum = data[1][i];
                    var numVisits = data[2][i];
                    lines[lineNum].numVisits = data[2][i];
                    lines[lineNum].lastVisit = numVisits > 0 ? tref + data[4][i] : undefined;
                }
                return lines;
            }

            $scope.showSource = function(clazz) {
                $http({
                    method:"GET",
                    url: "../sources/" +clazz.className +"?classLoader=" + clazz.classLoaderId
                }).then(function(response) {

                    var data = $scope.data.classes[clazz.id];

                    var sourceLines = response.data.split("\n");

                    var lines = [];

                    for (var i = 0; i < sourceLines.length; i++) {
                        var line = sourceLines[i];
                        lines.push({
                            lineNumber: i + 1,
                            lineIndex: i,
                            source: line
                        })
                    }
                    updateSourceLines(lines, data);

                    $scope.selectedSource = {
                        class: clazz,
                        lines: lines
                    };
                }, function() {
                    alert("Source not found")
                })
            }

            $scope.numVisits = function(classId, lineIndex) {
                var lines = $scope.data.classes[classId][1];
                for (var i = 0; i < lines.length; i++) {
                    var lineNum = lines[i];
                    if(lineNum == lineIndex) {
                        return $scope.data.classes[classId][2][i];
                    }

                }
                return undefined;
            }

            websocket.addListener(newData);

            $scope.data = "kj"
            websocket.join();
        }]);
angular.module("revoc")
    .controller("RevocController", ["$scope", "$http", "websocket", "classesParser",

        function ($scope, $http, websocket, classesParser) {


            function newData(newData) {
                $scope.$apply(function () {

                    var data = $scope.data;

                    if(data) {
                        for(var id in newData.classes) {
                            data.classes[id] = newData.classes[id];
                        }
                    } else {
                        data = newData;
                    }

                    $scope.data = data;

                    $scope.classes = classesParser($scope.data.classes);
                    $scope.loaders = parseLoaders($scope.data.loaders);
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

            $scope.getCoverage = function(id) {
                if(!$scope.data || !$scope.data.classes) {
                    return;
                }
                var data = $scope.data.classes;
                if(id && data && data[id]) {
                    var numLines = 0;
                    var covered = 0;
                    var lines = data[id][1];
                    var visits = data[id][2];
                    for(var l =0; l <  lines.length; l++) {
                        var runs = visits[l];
                        if(runs>=0) {
                            numLines++;
                        }
                        if(runs>=1) {
                            covered++;
                        }
                    }
                    if(numLines == 0) {
                        return 100;
                    } else {
                        return Math.floor(100*covered/numLines);
                    }
                } else {
                    return 0;
                }
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
                    if(clazz.lineId) {
                        console.log("Should scroll")
                        setTimeout(function() {
                            var e = document.getElementById("line-" + (clazz.lineId + 1));
                            if (e) {
                                e.scrollIntoView();
                                while(e) {
                                    console.log("Scrolltop of " + e + " is " + e.scrollTop)
                                    e = e.parentNode;
                                }
                                document.documentElement.scrollTop -= (80);
                                document.documentElement.scrollLeft = 0;
                            }


                        }, 100)
                    }
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

            websocket.join();
        }]);
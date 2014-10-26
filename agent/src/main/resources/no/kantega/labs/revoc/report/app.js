angular.module("revoc", [])
    .filter("simpleClassName", function () {
        return function (key) {
            if(!key) {
                return undefined;
            }
            var className = key.substr(key.indexOf("+") + 1);
            return  className.substr(className.lastIndexOf("/") + 1)
        }
    })
    .filter("className", function () {
        return function (key) {
            if(!key) {
                return undefined;
            }
            var className = key.substr(key.indexOf("+") + 1).replace(/\//g, ".");
            return  className;
        }
    })
    .filter("packageName", function () {
        return function (key) {
            if(!key) {
                return undefined;
            }
            var className = key.substr(key.indexOf("+") + 1);
            return  className.substr(0, className.lastIndexOf("/")).replace(/\//g, ".");
        }
    })
    .filter("byClassLoader", function() {
        return function(classes, classLoader) {
            if( !classLoader || !classes) {
                return classes;
            }
            var out = [];
            for (var i = 0; i < classes.length; i++) {
                var obj = classes[i];
                if(obj.classLoaderId == classLoader.id) {
                    out.push(obj);
                }
            }
            return out;
        }
    })
    .filter("durationSince", function () {
        return function (t, now) {
            if(!t) {
                return undefined;
            }
            var tt = ""
            var ss = parseInt((now - t) / 1000);
            if (ss <= 60) {
                tt = ss + "s";
            } else if (ss < 3600) {
                tt += parseInt(ss / 60) + "m";
            } else if (ss < 86400) {
                tt += parseInt(ss / 3600) + "h";
            } else {
                tt += parseInt(ss / 86400) + "d";
            }

            return tt;
        }
    })
    .service("websocket", function () {
        var _listeners = [];
        var ws = {
            _ws: null,

            addListener: function (listener) {
                _listeners.push(listener);
            },

            join: function () {
                // Safari must be >= 6
                if (navigator.userAgent.indexOf("Safari") != -1 && navigator.userAgent.indexOf("Version/5.") != -1) {
                    console.log("Detected Safari 5, Falling back to Ajax")
                    return false;
                }
                if (!window.WebSocket) {
                    console.log("Browser doesn't support WebSocket, Falling back to Ajax")
                    return false;
                }
                var location = "ws://" + window.location.hostname + (window.location.port != 80 ? ":" + window.location.port : "") + "/ws";
                console.log("Using URL: " + location)
                _ws = new WebSocket(location);
                _ws.onerror = this._onerror;
                _ws.onopen = this._onopen;
                _ws.onmessage = this._onmessage;
                _ws.onclose = this._onclose;
                console.log("Ready to join")
                return true;
            },

            _onerror: function (err) {
                console.log("Error: " + err)
            },
            _onopen: function () {
                // Not doing much
            },

            _send: function (user, message) {
                user = user.replace(':', '_');
                if (this._ws)
                    this._ws.send(user + ':' + message);
            },

            _onmessage: function (m) {
                var newData = eval("(" + m.data + ")");
                var size = 0, key;
                for (key in newData.classes) {
                    if (newData.classes.hasOwnProperty(key)) size++;
                }
                console.log("New data classes: " + size + " / " + m.data.length);

                if (size > 0) {
                    for (var i = 0; i < _listeners.length; i++) {
                        _listeners[i](newData)
                    }
                }
            },

            _onclose: function (m) {
                console.log("Closing WS: " + m)
                this._ws = null;
            }

        };

        return ws;
    })
    .service("classesParser", ["packageNameFilter", "simpleClassNameFilter", function (packageNameFilter, simpleNameFilter) {

        function compareClasses(left, right) {
            if(!left) {
                return parseClass(right);
            }

            var numLines = 0;
            var numLinesRun = 0;
            var sumLinesRun = 0;
            var maxLinesRun = 0;

            var linesRight = right[1];
            var visitsRight = right[2];
            var linesLeft = left[1];
            var visitsLeft= left[2];

            var linesLeftLookup = {};


            for (var l = 0, m = linesLeft.length; l < m; l++) {
                linesLeftLookup[linesLeft[l]] = visitsLeft[l];
            }
            for (var l = 0, m = linesRight.length; l < m; l++) {
                var vl = linesLeftLookup[linesRight[l]] || 0;

                var diff = visitsRight[l] - vl;
                if (diff > 0 ) {
                    numLinesRun++;
                    sumLinesRun += diff;
                }

                maxLinesRun = Math.max(diff, maxLinesRun);
                numLines++;

            }

            return {
                run: numLinesRun,
                notrun: numLines - numLinesRun,
                lines: numLines,
                coverage: Math.round(numLinesRun * 100 / numLines),
                sum: sumLinesRun,
                max: maxLinesRun
            };
        }

        function parseClass(classData) {
            var numLines = 0;
            var numLinesRun = 0;
            var sumLinesRun = 0;
            var maxLinesRun = 0;

            var lines = classData[1];
            var visits = classData[2];
            for (var l = 0, m = lines.length; l < m; l++) {
                var r = visits[l];
                if (r > 0) {
                    numLinesRun++;
                    sumLinesRun += r;
                }
                maxLinesRun = Math.max(r, maxLinesRun);
                numLines++;

            }
            return {
                run: numLinesRun,
                notrun: numLines - numLinesRun,
                lines: numLines,
                coverage: Math.round(numLinesRun * 100 / numLines),
                sum: sumLinesRun, max: maxLinesRun
            };
        }
        return {
            parseClasses: function (data) {
                var array = [];

                var totalNumLinesRun = 0;
                var totalNumLinesNotRun = 0;
                var totalNumLines = 0;
                var totalMax = 0;
                var totalSumRun = 0;

                for (var c in data) {
                    var item = parseClass(data[c]);
                    item.index= array.length;
                    item.id= c;
                    item.classLoaderId= c.substr(0, c.indexOf("+"));
                    item.className= c.substr(c.indexOf("+")+1);
                    item.package= packageNameFilter(c);
                    item.simpleName= simpleNameFilter(c);
                    array.push(item)
                }
                return {
                    classes: array,
                    total: {
                        run: totalNumLinesRun,
                        notrun: totalNumLinesNotRun,
                        lines: totalNumLines,
                        coverage: Math.round(totalNumLinesRun * 100 / totalNumLines),
                        sum: totalSumRun,
                        max: totalMax
                    }
                };
            },
            parseRecording: function (rec) {

                var array = [];
                var startClasses = rec.start.data.classes;
                var endClasses = rec.end.data.classes;
                for(var s in  endClasses) {
                    var diff = compareClasses(startClasses[s], endClasses[s]);

                    if(diff.run != 0) {
                        diff.index= array.length;
                        diff.id= s;
                        diff.classLoaderId= s.substr(0, s.indexOf("+"));
                        diff.className= s.substr(s.indexOf("+")+1);
                        diff.package= packageNameFilter(s);
                        diff.simpleName= simpleNameFilter(s);
                        array.push(diff);
                    }                  ''
                }
                return {
                    classes: array,
                    total: {
                        run: 0,
                        notrun: 0,
                        lines: 0,
                        coverage: 0,
                        sum: 0,
                        max: 0
                    }
                };
            }
        }
    }]);

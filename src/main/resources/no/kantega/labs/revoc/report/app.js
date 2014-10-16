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
        return function (data) {
            var array = new Array();

            var totalNumLines = 0;
            var totalNumLinesRun = 0;
            var totalNumLinesNotRun = 0;
            var totalMax = 0;
            var totalSumRun = 0;

            var i = 0;
            for (var c in data) {
                var numLines = 0;
                var numLinesRun = 0;
                var sumLinesRun = 0;
                var maxLinesRun = 0;

                var lines = data[c][1];
                var visits = data[c][2];
                for (var l = 0, m = lines.length; l < m; l++) {
                    var r = visits[l];
                    if (r > 0) {
                        numLinesRun++;
                        totalNumLinesRun++;
                        sumLinesRun += r;
                        totalSumRun += r;
                    }

                    maxLinesRun = Math.max(r, maxLinesRun);
                    totalMax = Math.max(r, totalMax);
                    numLines++;
                    totalNumLines++;

                }
                totalNumLinesNotRun += (numLines - numLinesRun);
                array.push({
                    index: i++,
                    id: c,
                    classLoaderId: c.substr(0, c.indexOf("+")),
                    className: c.substr(c.indexOf("+")+1),
                    package:packageNameFilter(c),
                    simpleName: simpleNameFilter(c),
                    run: numLinesRun,
                    notrun: numLines - numLinesRun,
                    lines: numLines,
                    coverage: Math.round(numLinesRun * 100 / numLines),
                    sum: sumLinesRun, max: maxLinesRun
                });
            }
            return {
                classes: array,
                total: {
                    run: totalNumLines,
                    notrun: totalNumLinesNotRun,
                    lines: totalNumLines,
                    coverage: Math.round(totalNumLinesRun * 100 / totalNumLines),
                    sum: totalSumRun,
                    max: totalMax
                }
            };
        }
    }]);

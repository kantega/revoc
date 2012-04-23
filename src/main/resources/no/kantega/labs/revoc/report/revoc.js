/*
 * Copyright 2012 Kantega AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

window.addEventListener("load", function() {


    var requestAnimationFrame = window.requestAnimationFrame || window.mozRequestAnimationFrame ||
                            window.webkitRequestAnimationFrame || window.msRequestAnimationFrame ||
                            window.oRequestAnimationFrame || function(fun, elem) {setTimeout(fun, 10)};

    var data;

    var zoom = 2;

    var prevLine;

    var currentClick;

    var currentSource;

    var maxLines = 0;

    var idx2cn;
    var cn2idx;

    var canvas, ctx;

    var ws = {
        _ws: null,

        join: function() {
            if (!window.WebSocket) {
                return false;
            }
            var location = document.location.toString().replace('http://', 'ws://').replace('https://', 'wss://') + "ws/ws";
            _ws = new WebSocket(location);
            _ws.onopen = this._onopen;
            _ws.onmessage = this._onmessage;
            _ws.onclose = this._onclose;
            return true;
        },

        _onopen: function() {
            // Not doing much
        },

        _send: function(user, message) {
            user = user.replace(':', '_');
            if (this._ws)
                this._ws.send(user + ':' + message);
        },

        _onmessage: function(m) {
            console.log("New data");
            var newData = eval("(" + m.data + ")");
            var first = !data;
            gotNewData(newData);
            if (first) {
                getSourceReport({className: idx2cn[0], lineId: 0});
            }
        },

        _onclose: function(m) {
            this._ws = null;
        }

    };


    function gotNewData(newData) {

        var changed = false;
        if(currentClick) {
            var newDataHasClass = newData[currentClick.className] != undefined;
            if(data && newDataHasClass && isClassChanged(data, newData, currentClick.className)) {
                changed = true;
            }

        }

        if(data) {
            console.log("Merging new data")
            for(var cn in newData) {
                data[cn] = newData[cn];
            }
        } else {
            data = newData;
        }
        idx2cn = new Array();
        cn2idx = new Array();
        var c = 0;
        for (var className in data) {
            idx2cn[c] = className;
            cn2idx[className] = c;
            c++;

        }

        if (changed) {
            getSourceReport(currentClick, false);
        }
        drawData();
    }

    function updateData(props) {

        var xhr = new XMLHttpRequest();

        var url = "json?d=" + new Date().getTime()

        if (props.hang)
            url += "&hang";

        xhr.open("GET", url, true);

        xhr.onreadystatechange = function() {
            if (xhr.readyState == 4) {
                if ((xhr.status == 0 || xhr.status == 200) && xhr.responseText) {
                    var newData = eval("(" + xhr.responseText + ")");

                    gotNewData(newData);

                    if (props.onData) {
                        props.onData(data);
                    }
                    setTimeout("updateData({hang: true})", 1000)
                } else {
                    // Server might be down, retry in a bit
                    setTimeout("updateData({hang: false})", 2000)
                }

            }
        };
        xhr.send(null)
    }

    function isClassChanged(data, newData, className) {

        var oldLines = data[className][1];
        var newLines = newData[className][1];
        if (oldLines.length != newLines.length) {
            return true;
        }
        for (var i = 0; i < newLines.length; i++) {
            if (newLines[i] != oldLines[i]) {
                return true;
            }
        }
        return false;
    }

    function drawLine(className, highlight) {
        var c = cn2idx[className];

        var classData = data[className];
        var lines = classData[1];
        var lastTime = classData[2];
        var times= classData[3];
        var now = new Date().getTime();


        ctx.fillStyle = highlight ? "rgb(110,110,110)" : "rgb(0,0,0)";
        ctx.fillRect(c * zoom, 0, zoom, maxLines * zoom);

        for (var l = 0; l < lines.length; l++) {
            var val = lines[l];
            if (val != -1) {
                if (val == 0) {
                    ctx.fillStyle = highlight ? "rgb(255,0,0)" : "rgb(255,100,100)";
                } else if (val > 0) {
                    var rel = times[l];
                    var lastAccess  = lastTime - rel;
                    var recency = now-lastAccess;
                    var a = highlight ? 1 : 0.9;

                    ctx.fillStyle = recency < 10000 ? "rgba(0,255,255," +a +")" :"rgba(0,255,0," +a +")"


                }
                ctx.fillRect(c * zoom, l * zoom, zoom, zoom);

            }
        }
    }

    function drawData() {
        console.log("Drawing data")
        maxLines = 0;
        var classes = 0;
        for (var i in data) {
            maxLines = Math.max(maxLines, data[i][1].length);
            classes++;
        }


        var width = classes;
        var height = maxLines;


        if (width < 10) {
            zoom = 10;
        } else if (width < 200) {
            zoom = 3;
        }
        width = width * zoom;
        height = height * zoom;

        document.getElementById("canvasscroll").style.width = (width + 15) + "px";
        document.getElementById("sourcecolumn").style.width = (window.outerWidth - width - 35) + "px";
        document.getElementById("source").style.width = (window.outerWidth - width - 35) + "px";

        canvas.setAttribute("width", width);
        canvas.setAttribute("height", height);

        ctx.fillStyle = "rgb(0,0,0)";

        ctx.fillRect(0, 0, canvas.width, canvas.height);

        drawLines();


    }

    function drawLines() {
        for (var className in data) {
            drawLine(className, prevLine && prevLine.className == className);
        }
    }

    function getCanvasPos() {
        var cv = document.getElementById("canvas")
        var x = cv.offsetLeft;
        var y = cv.offsetTop;

        var element = cv.offsetParent;

        while (element !== null) {
            x = parseInt(x) + parseInt(element.offsetLeft);
            y = parseInt(y) + parseInt(element.offsetTop);

            element = element.offsetParent;
        }
        return new Array(x, y);
    }

    function getCoverage(className) {
        if(data && data[className]) {
            var numLines = 0;
            var covered = 0;
            var lines = data[className][1];
            for(var l =0; l <  lines.length; l++) {
                var runs = lines[l];
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
    function canvasmoved(evt) {
        var ctx = document.getElementById("canvas").getContext("2d");

        var clsLine = getClassAndLine(evt);

        if (prevLine) {
            drawLine(prevLine.className, false);
        }

        drawLine(clsLine.className, true);

        document.getElementById("title").innerHTML = clsLine.className.replace(/\//g, ".") + ": " + (clsLine.lineId + 1);
        document.getElementById("coverage").innerHTML = getCoverage(clsLine.className) +"%";

        prevLine = clsLine
    }

    function getSourceReport(clsLine, scroll) {
        if (!currentClick || clsLine.className != currentClick.className) {
            var xhr = new XMLHttpRequest();

            xhr.open("GET", "sources/" + clsLine.className + "?classLoader=" + data[clsLine.className][0], true);

            xhr.onreadystatechange = function() {
                if (xhr.readyState == 4) {
                    currentSource = xhr.responseText;
                    showSource(xhr.responseText, clsLine, scroll);
                }
            };
            xhr.send(null)
            currentClick = clsLine;

        } else {
            showSource(currentSource, clsLine, scroll);
        }
    }

    function showSource(sourceText, clsLine, scroll) {
        var source = document.getElementById("source");
        source.innerHTML = "";

        var tbl = document.createElement("table");

        source.appendChild(tbl);


        var cls = data[clsLine.className];

        var lines = sourceText.split("\n");

        var bpByLine = {};
        var bps = cls[2];
        for (var b in bps) {
            if (!bpByLine[bps[b][0]]) bpByLine[bps[b][0]] = new Array();
            bpByLine[bps[b][0]].push(bps[b]);
        }

        var visits = cls[1];

        for (var l = 0; l < lines.length; l++) {

            var lv = l >= visits.length ? -1 : visits[l];

            var line = document.createElement("tr");


            var className = lv == -1 ? "noline" : lv == 0 ? "nonvisited" : "visited";

            var singleBranched = false;
            var branches = "";
            if (bpByLine[l]) {
                className += " branched";
                for (var bi in bpByLine[l]) {
                    var bp = bpByLine[l][bi];
                    var jmp = bp[1] - bp[2];
                    var nojmp = bp[2];
                    if ((jmp == 0 && nojmp > 0 ) || (jmp > 0 && nojmp == 0)) {
                        singleBranched = true;
                    }
                    if (branches != "") {
                        branches += ", ";
                    }
                    branches += jmp + "/" + nojmp;
                }
            }
            if (singleBranched)
                className += " singlebranched";

            line.setAttribute("class", className);


            line.setAttribute("id", "line-" + (l + 1));

            var ln = document.createElement("td");
            ln.setAttribute("class", "linenumber");
            ln.innerHTML = (l + 1);
            line.appendChild(ln);

            var nv = document.createElement("td");
            nv.setAttribute("class", "numvisits");
            nv.innerHTML = lv >= 0 ? lv : "";
            line.appendChild(nv);

            var cond = document.createElement("td");
            cond.setAttribute("class", "conditional");
            cond.innerHTML = lv > 0 ? branches : "";
            line.appendChild(cond);

            var time = document.createElement("td");
            cond.setAttribute("class", "lastvisit");
            if (l < visits.length) {
                var now = new Date().getTime();

                var trel = cls[3][l];
                var t = cls[2]-trel;
                var ss = parseInt((now - t) / 1000);
                var tt = ""
                if (trel < 0) {

                } else if (ss <= 60) {
                    tt = ss + "s";
                } else if (ss < 3600) {
                    tt += parseInt(ss / 60) + "m";
                } else if (ss < 86400) {
                    tt += parseInt(ss / 3600) + "h";
                } else {
                    tt += parseInt(ss / 86400) + "d";
                }
                cond.innerHTML = tt;
            }
            line.appendChild(time);

            var src = document.createElement("td");
            src.setAttribute("class", "sourceline");
            var pre = document.createElement("pre");
            pre.appendChild(document.createTextNode(lines[l]));
            src.appendChild(pre);
            line.appendChild(src);
            tbl.appendChild(line)
        }

        source.style.display = "block";

        if (scroll) {
            scrollSourceTo(clsLine)
        }
    }

    function scrollSourceTo(clsLine) {
        var e = document.getElementById("line-" + (clsLine.lineId + 1));
        if (e) e.scrollIntoView();
        var cs = document.getElementById("canvasscroll");
        document.documentElement.scrollTop -= (cs.offsetHeight + 20);
        document.documentElement.scrollLeft = 0;
    }

    function canvasclicked(evt) {
        var clsLine = getClassAndLine(evt);

        if (!currentClick || currentClick.className != clsLine.className) {
            getSourceReport(clsLine, true);
        } else {
            scrollSourceTo(clsLine);
        }
        currentClick = clsLine;
    }

    function getClassAndLine(evt) {
        var cvs = document.getElementById("canvasscroll");
        var cpos = getCanvasPos();

        var mx = evt.clientX - cpos[0] + cvs.scrollLeft;
        var my = evt.clientY - cpos[1] + cvs.scrollTop;


        var clsId = parseInt(mx / zoom);
        var classLength = data[idx2cn[clsId]][1].length;
        var wouldBeLength = parseInt(my / zoom);
        var lineId = Math.min(classLength - 1, wouldBeLength);
        console.log("lineId/clsId: " + wouldBeLength + "/" + clsId);
        return {className: idx2cn[clsId], lineId: lineId};
    }

    function canvasout() {
        document.getElementById("footer").style.display = "none";
        if (prevLine) {
            drawLine(prevLine.className, document.getElementById("canvas").getContext("2d"), false)
            prevLine = undefined;
        }
    }

    function canvasenter() {
        document.getElementById("footer").style.display = "block";
    }


    if (!ws.join()) {
        console.log("Websocket not supported, falling back to Ajax")
        updateData({hang: false,
            onData: function(d) {
                if (idx2cn[0]) {
                    getSourceReport({className: idx2cn[0], lineId: 0});
                }
            }
        });

    }

    function animloop() {
        drawLines();
        requestAnimationFrame(animloop)
    }
    canvas = document.getElementById("canvas");

    ctx = canvas.getContext("2d");




    canvas.addEventListener("mousemove", canvasmoved);
    canvas.addEventListener("click", canvasclicked);
    canvas.addEventListener("mouseout", canvasout);
    canvas.addEventListener("mouseover", canvasenter);

    animloop();

});
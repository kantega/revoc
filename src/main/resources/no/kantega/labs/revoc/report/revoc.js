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

    $ = function(sel) {
        return document.querySelector(sel);
    };
    var data;

    var zoom = 2;

    var prevLine;

    var currentClick;

    var currentSource;

    var maxLines = 0;

    var idx2cn;
    var cn2idx;

    var smallCanvas, canvas, ctx, smallCtx, source, canvasScroll;

    var smallCanvasDragging = false;

    var redrawTimeout;

    var ws = {
        _ws: null,

        join: function() {
            // Safari must be >= 6
            if(navigator.userAgent.indexOf("Safari") != -1 && navigator.userAgent.indexOf("Version/5.") !=-1) {
                console.log("Detected Safari 5, Falling back to Ajax")
                return false;
            }
            if (!window.WebSocket) {
                console.log("Browser doesn't support WebSocket, Falling back to Ajax")
                return false;
            }
            var location = document.location.toString().replace('http://', 'ws://').replace('https://', 'wss://') + "ws/ws";
            _ws = new WebSocket(location);
            _ws.onerror = this._onerror;
            _ws.onopen = this._onopen;
            _ws.onmessage = this._onmessage;
            _ws.onclose = this._onclose;
            console.log("Ready to join")
            return true;
        },

        _onerror: function(err) {
            console.log("Error: " + err)
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
            var newData = eval("(" + m.data + ")");
            var size = 0, key;
            for (key in newData) {
                if (newData.hasOwnProperty(key)) size++;
            }
            console.log("New data classes: " + size + " / " + m.data.length);

            if(size > 0) {
                var first = !data;
                gotNewData(newData);
                if (first) {
                    console.log("Getting source for: " +idx2cn[0] + ", line " + 0);
                    getSourceReport({className: idx2cn[0], lineId: 0});
                }
            }
        },

        _onclose: function(m) {
            console.log("Closing WS: " +m)
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
        if(redrawTimeout) {
            clearTimeout(redrawTimeout);
        }
        redrawTimeout = setTimeout(drawData, 11000)
    }

    function isClassChanged(data, newData, className) {

        var oldLines = data[className][2];
        var newLines = newData[className][2];
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
        var visits = classData[2];
        var lastTime = classData[3];
        var times= classData[4];
        var now = new Date().getTime();


        ctx.fillStyle = highlight ? "rgb(110,110,110)" : "rgb(0,0,0)";
        ctx.fillRect(c * zoom, 0, zoom, maxLines * zoom);

        for (var l = 0; l < lines.length; l++) {
            var line = lines[l];
            var val = visits[l];

            if (val == 0) {
                ctx.fillStyle = highlight ? "rgb(255,0,0)" : "rgb(255,100,100)";
            } else if (val > 0) {
                var rel = times[l];
                var lastAccess  = lastTime - rel;
                var recency = now-lastAccess;

                ctx.fillStyle = recency < 10000 ? "rgb(0,200,255)" :"rgb(0,255,0)"


            }
            ctx.fillRect(c * zoom, line * zoom, zoom, zoom);


        }
    }

    function drawData() {
        maxLines = 0;
        var classes = 0;
        for (var i in data) {
            var lines = data[i][1];
            maxLines = Math.max(maxLines, lines[lines.length-1]+1);
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

        canvas.setAttribute("width", width);
        canvas.setAttribute("height", height);

        ctx.fillStyle = "rgb(0,0,0)";

        ctx.fillRect(0, 0, canvas.width, canvas.height);

        drawLines();
        drawSmall();


    }

    function drawSmall() {
        var w = canvas.width/10;
        smallCanvas.width = w;
        var h = canvas.height/10;
        smallCanvas.height = h;

        smallCtx.drawImage(canvas, 0, 0, w, h)

        var cs = canvasScroll;

        smallCtx.fillStyle = "rgba(255,255,255,0.2)";
        smallCtx.strokeStyle = "rgb(255,255,255)";
        smallCtx.rect(cs.scrollLeft/10, cs.scrollTop/10, cs.offsetWidth/10,cs.offsetHeight/10);
        smallCtx.fill();
        smallCtx.stroke();
    }

    function drawLines() {
        for (var className in data) {
            drawLine(className, prevLine && prevLine.className == className);
        }
    }

    function getElemPos(elem) {
        var x = elem.offsetLeft;
        var y = elem.offsetTop;

        var element = elem.offsetParent;

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
            var visits = data[className][2];
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
    function canvasmoved(evt) {
        if(!data) {
            return;
        }
        var clsLine = getClassAndLine(evt);
        if (prevLine) {
            drawLine(prevLine.className, false);
        }

        drawLine(clsLine.className, true);

        $("#title").innerHTML = clsLine.className.replace(/\//g, ".") + ": " + (clsLine.lineId + 1);
        $("#coverage").innerHTML = getCoverage(clsLine.className) +"%";

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
            updateSource();
        }
    }

    function updateTime() {
        if(currentClick) {
            var cls = data[currentClick.className];

            var times = cls[4];
            var tref = cls[3];
            var lines = cls[1];

            var now = new Date().getTime();

            for(var i = 0,m=lines.length; i<m;i++) {
                var trel = times[i];
                if(trel >= 0)
                $("#time-"+(lines[i]+1)).innerHTML = formatSince(trel, tref, now)
            }
        }
    }

    function updateSource() {

        if(currentClick) {

            var cls = data[currentClick.className];

            var lines = cls[1];
            var visits = cls[2];

            for(var i = 0,m=lines.length; i<m;i++) {
                var line = lines[i];
                var n = visits[i];
                var row = $("#line-"+(line+1));
                if(n != -1) {
                    $("#nv-"+(line+1)).innerHTML = n;
                }


                row.setAttribute("class", n > 0 ? "visited" : "nonvisited");


            }
            updateTime();
        }
    }
    function showSource(sourceText, clsLine, scroll) {

        source.innerHTML = "";
        $("#sourcetitle").innerHTML = clsLine.className.replace(/\//g,".");
        $("#sourcecoverage").innerHTML = getCoverage(clsLine.className) +"%";

        var tbl = document.createElement("table");

        source.appendChild(tbl);


        var cls = data[clsLine.className];

        var lines = sourceText.split("\n");

        for (var l = 0; l < lines.length; l++) {

            var line = document.createElement("tr");

            line.setAttribute("id", "line-" + (l + 1));

            var ln = document.createElement("td");
            ln.setAttribute("class", "linenumber");
            ln.innerHTML = (l + 1);
            line.appendChild(ln);

            var nv = document.createElement("td");
            nv.setAttribute("class", "numvisits");
            nv.setAttribute("id", "nv-" +(l+1));
            line.appendChild(nv);

            /*
            var cond = document.createElement("td");
            cond.setAttribute("class", "conditional");
            line.appendChild(cond);
            */

            var time = document.createElement("td");
            time.setAttribute("id", "time-" +(l+1));
            time.setAttribute("class", "lastvisit");
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

        updateSource();

        if (scroll) {
            scrollSourceTo(clsLine)
        }
    }

    function formatSince(trel, tref, now) {
        var tt = ""
        if (trel >= 0) {
            var t = tref -trel;
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
        }
        return tt;
    }

    function scrollSourceTo(clsLine) {
        var e = $("#line-" + (clsLine.lineId + 1));
        if (e) e.scrollIntoView();
        var cs = $("#canvasscroll");

        document.body.scrollTop -= (120);
        document.body.scrollTop.scrollLeft = 0;
    }

    function canvasclicked(evt) {
        evt.preventDefault();
        $("#source").style.display="block";
        var clsLine = getClassAndLine(evt);

        if (!currentClick || currentClick.className != clsLine.className) {
            getSourceReport(clsLine, true);
        } else {
            scrollSourceTo(clsLine);
        }
        currentClick = clsLine;
    }

    function getClassAndLine(evt) {
        var cvs = $("#canvasscroll");
        var cpos = getElemPos(cvs);

        var mx = evt.clientX - cpos[0] + cvs.scrollLeft;
        var my = evt.clientY - cpos[1] + cvs.scrollTop;


        var clsId = parseInt(mx / zoom);
        var cls = data[idx2cn[clsId]];
        var classLength = cls[1][cls[1].length-1];
        var wouldBeLength = parseInt(my / zoom);
        var lineId = Math.min(classLength, wouldBeLength);
        return {className: idx2cn[clsId], lineId: lineId};
    }

    function canvasout() {
        document.getElementById("footer").style.display = "none";
        if (prevLine) {
            drawLine(prevLine.className, document.getElementById("canvas").getContext("2d"), false)
            prevLine = undefined;
        }
    }

    function canvaswheel(evt) {
        evt.preventDefault();
        console.log(evt);
        canvasScroll.scrollLeft -= evt.wheelDeltaX;
        canvasScroll.scrollTop -= evt.wheelDeltaY;
        drawSmall();
    }
    function canvasenter() {
        $("#footer").style.display = "block";
    }

    function smallCanvasPaint(evt) {
        var cpos = getElemPos(smallCanvas);

        var mx = evt.clientX - cpos[0] + smallCanvas.scrollLeft;

        var my = evt.clientY - cpos[1] + smallCanvas.scrollTop;

        canvasScroll.scrollLeft = mx*10 -canvasScroll.offsetWidth/2;
        canvasScroll.scrollTop = my*10 -canvasScroll.offsetHeight/2;
        drawSmall();
    }

    function smallCanvasMove(evt) {
        if(smallCanvasDragging) {
            evt.preventDefault();
            smallCanvasPaint(evt);
        }

    }
    function smallCanvasDown(evt) {
        evt.preventDefault();
        smallCanvasDragging = true;
        smallCanvasPaint(evt);
    }
    function smallCanvasUp(evt) {
        evt.preventDefault();
        smallCanvasDragging = false;
        smallCanvasPaint(evt);
    }

    function updateData(props) {

        var xhr = new XMLHttpRequest();

        var url = "json?d=" + new Date().getTime()

        xhr.open("GET", url, true);

        xhr.onreadystatechange = function() {
            if (xhr.readyState == 4) {
                if ((xhr.status == 0 || xhr.status == 200) && xhr.responseText) {
                    var newData = eval("(" + xhr.responseText + ")");

                    gotNewData(newData);

                    if (props.onData) {
                        props.onData(data);
                    }
                    //setTimeout("updateData({hang: true})", 1000)
                } else {
                    // Server might be down, retry in a bit
                    //setTimeout("updateData({hang: false})", 2000)
                }

            }
        };
        xhr.send(null)
    }

    function createTotalLine(totalNumLinesRun, totalNumLinesUnRun, totalNumLines, totalSumRun, totalMax) {
        var total = document.createElement("tr");
        total.className = "total";
        var label = createElement("td", "Total");
        label.className = "label";
        total.appendChild(label);
        total.appendChild(createElement("td", totalNumLinesRun));
        total.appendChild(createElement("td", totalNumLinesUnRun));
        total.appendChild(createElement("td", totalNumLines));
        total.appendChild(createElement("td", Math.round(totalNumLinesRun * 100 / totalNumLines) + "%"));
        total.appendChild(createElement("td", totalSumRun));
        total.appendChild(createElement("td", totalMax));
        return total;
    }

    var desc = 1;
    var overviewSort = null;


    function showOverview(evt) {
        $("#source").style.display = "none";
        document.body.scrollTop =0;
        if(evt)
            evt.preventDefault();

        var array = new Array();

        var totalNumLines = 0;
        var totalNumLinesRun = 0;
        var totalNumLinesUnRun = 0;
        var totalMax = 0;
        var totalSumRun = 0;

        for(var c in data) {
            var numLines = 0;
            var numLinesRun = 0;
            var sumLinesRun = 0;
            var maxLinesRun = 0;

            var lines = data[c][1];
            var visits = data[c][2];
            for(var l= 0, m=lines.length; l < m; l++) {
                var r = visits[l];
                if(r > 0) {
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
            totalNumLinesUnRun += (numLines-numLinesRun);
            array.push([c, numLinesRun, numLines-numLinesRun, numLines, Math.round(numLinesRun*100 / numLines), sumLinesRun, maxLinesRun]);
        }
        if(overviewSort)
            array.sort(overviewSort);



        var overview = $("#overview");
        while(overview.firstChild) {
            overview.removeChild(overview.firstChild);
        }
        overview.style.display="block";

        var table = document.createElement("table");


        var tr = document.createElement("tr");
        var name = createElement("th", "Name");

        name.addEventListener("click", function() {
            overviewSort = null;
            showOverview();
        });

        tr.appendChild(name);
        var run = createElement("th", "Run");
        run.setAttribute("class", "sortable");
        run.addEventListener("click", function() {
            desc = desc *-1;
            overviewSort = function(a, b) {return (desc*(b[1] - a[1]))};
            showOverview();
        });
        tr.appendChild(run);

        var unRun = createElement("th", "UnRun");
        unRun.setAttribute("class", "sortable");
        unRun.addEventListener("click", function() {
            desc = desc *-1;
            overviewSort = function(a, b) {return (desc*(b[2] - a[2]))};
            showOverview();
        });
        tr.appendChild(unRun);
        var linesCol = createElement("th", "Lines");
        linesCol.setAttribute("class", "sortable");
        linesCol.addEventListener("click", function() {
            desc = desc *-1;
            overviewSort = function(a, b) {return (desc*(b[3] - a[3]))};
            showOverview();
        });
        tr.appendChild(linesCol);
        var coverage = createElement("th", "Cover");
        coverage.setAttribute("class", "sortable");
        coverage.addEventListener("click", function() {
            desc = desc *-1;
            overviewSort = function(a, b) {return (desc*(b[4] - a[4]))};
            showOverview();
        });
        tr.appendChild(coverage);
        var sumCol = createElement("th", "Sum");
        sumCol.setAttribute("class", "sortable");
        sumCol.addEventListener("click", function() {
            desc = desc *-1;
            overviewSort = function(a, b) {return (desc*(b[5] - a[5]))};
            showOverview();
        });
        tr.appendChild(sumCol);

        var maxi = createElement("th", "Max");
        maxi.addEventListener("click", function() {
            desc = desc *-1;
            overviewSort = function(a, b) {return (desc*(b[6] - a[6]))};
            showOverview();
        });
        maxi.setAttribute("class", "sortable");

        tr.appendChild(maxi);

        table.appendChild(tr);

        table.appendChild(createTotalLine(totalNumLinesRun, totalNumLinesUnRun, totalNumLines, totalSumRun, totalMax));

        for(var i= 0,m=array.length; i<m; i++) {
            var a = array[i];
            tr = document.createElement("tr");
            tr.setAttribute("class", "class " + (i%2 == 0 ? "even" : "odd"));
            var td = document.createElement("td");
            tr.setAttribute("rclass", a[0]);
            tr.addEventListener("click", function(evt) {
                $("#overview").style.display="none";

                var className = this.getAttribute("rclass");
                var line = 0;
                var lines = data[className][1];
                for(var l = 0, m=lines.length; l < m; l++) {
                    if(lines[l] != -1) {
                        line = l;
                        break;
                    }
                }
                $("#source").style.display="none";
                getSourceReport({className: className,lineId:line}, true)
            });
            var li = a[0].lastIndexOf("/");

            var pack = createElement("div", a[0].substr(0, li).replace(/\//g,"."));
            pack.setAttribute("class", "package")
            td.appendChild(pack);
            var cName = createElement("div", a[0].substr(li+1));
            cName.setAttribute("class", "className")
            td.appendChild(cName);
            td.setAttribute("class", "label");
            tr.appendChild(td);
            table.appendChild(tr);

            tr.appendChild(createElement("td", a[1]));
            tr.appendChild(createElement("td", a[2]));
            tr.appendChild(createElement("td", a[3]));
            tr.appendChild(createElement("td", a[4] +"%"));
            tr.appendChild(createElement("td", a[5]));
            tr.appendChild(createElement("td", a[6]));


        }


        table.appendChild(createTotalLine(totalNumLinesRun, totalNumLinesUnRun, totalNumLines, totalSumRun, totalMax));


        overview.appendChild(table);



    }

    if (!ws.join()) {
        updateData({hang: false,
            onData: function(d) {
                if (idx2cn[0]) {
                    getSourceReport({className: idx2cn[0], lineId: 0});
                }
            }
        });


    }


    smallCanvas = $("#smallCanvas");
    canvas = $("#canvas");
    canvasScroll = $("#canvasscroll");
    source = $("#source");
    ctx = canvas.getContext("2d");


    smallCtx = smallCanvas.getContext("2d");




    canvas.addEventListener("mousemove", canvasmoved);
    canvas.addEventListener("click", canvasclicked);
    canvas.addEventListener("mouseout", canvasout);
    canvas.addEventListener("mouseover", canvasenter);
    canvas.addEventListener("mousewheel", canvaswheel);

    smallCanvas.addEventListener("mousedown", smallCanvasDown);
    smallCanvas.addEventListener("mouseup", smallCanvasUp);
    smallCanvas.addEventListener("mouseout", function() {smallCanvasDragging=false});
    smallCanvas.addEventListener("mousemove", smallCanvasMove);

    $("#pixelsheading").addEventListener("click", function() {
        var p = $("#pixels");
        if(p.getAttribute("class")) {
            p.setAttribute("class", "");
            this.innerHTML ="Hide navigation"
        } else {
            p.setAttribute("class", "hidden");
            this.innerHTML ="Show navigation"
        }

    });

    setInterval(updateTime, 1000);

    $("#showclasses").addEventListener("click", showOverview);

    function createElement(name, content) {
        var td = document.createElement(name);
        td.innerHTML = content;
        return td;
    }



});
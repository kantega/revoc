angular.module("revoc")
    .directive("revocPixels", ["$parse", function($parse) {

        function link(scope, elem, attrs) {
            console.log("Linking " + elem)

            var data;

            var zoom = 2;
            var maxLines = 0;

            var idx2cn;
            var cn2idx;

            var prevLine;

            var small = document.createElement("canvas");
            small.setAttribute("class", "smallCanvas");
            elem[0].appendChild(small);

            var scroll = document.createElement("div");
            scroll.setAttribute("class", "canvasscroll");
            elem[0].appendChild(scroll);

            var canvas = document.createElement("canvas");
            canvas.setAttribute("class", "canvas");
            scroll.appendChild(canvas);

            var ctx = canvas.getContext("2d");
            var smallCtx = small.getContext("2d");

            angular.element(canvas).on("mousewheel", canvaswheel);
            angular.element(canvas).on("mousemove", canvasmoved);
            angular.element(canvas).on("mouseout", canvasout);
            angular.element(canvas).on("click", canvasclicked);

            angular.element(small).on("mousedown", smallCanvasDown);
            angular.element(small).on("mouseup", smallCanvasUp);
            angular.element(small).on("mouseout", function() {smallCanvasDragging=false});
            angular.element(small).on("mousemove", smallCanvasMove);


            var redraw;

            var smallCanvasDragging = false;

            scope.$watch("revocPixels", function(value) {
                if(value) {
                    console.log("Data changed to: " + value)
                    data = value;
                }
            });

            scope.$watch("revocDataVersion", function(value) {
                if(value) {
                    console.log("Data version changed to: " + value)
                    if(redraw) {
                        clearTimeout(redraw);
                    }
                    drawData();
                    redraw = setTimeout(drawData, 11000);
                }
            });

            function canvasclicked(evt) {
                var clsLine = getClassAndLine(evt);

                if(scope.onPixelClick) {
                    scope.$apply(function() {scope.onPixelClick({pixel: clsLine})});
                }
            }

            function canvasout() {
                if (prevLine) {
                    drawLine(prevLine.id, false);
                    prevLine = undefined;
                }
                if(scope.onPixelHover) {
                    scope.$apply(function() {scope.onPixelHover({classLine: null})});
                }
            }

            function canvaswheel(evt) {
                evt.preventDefault();
                scroll.scrollLeft -= evt.wheelDeltaX;
                scroll.scrollTop -= evt.wheelDeltaY;
                drawSmall();
            }

            function canvasmoved(evt) {

                if(!data) {
                    return;
                }
                var clsLine = getClassAndLine(evt);

                if (prevLine) {
                    drawLine(prevLine.id, false);
                }

                drawLine(clsLine.id, true);

                if(scope.onPixelHover) {
                    scope.$apply(function() {scope.onPixelHover({pixel: clsLine})});
                }

                prevLine = clsLine
            }

            function smallCanvasPaint(evt) {
                var cpos = getElemPos(small);

                var mx = evt.clientX - cpos[0] + small.scrollLeft;

                var my = evt.clientY - cpos[1] + small.scrollTop;

                scroll.scrollLeft = mx*10 -small.offsetWidth/2;
                scroll.scrollTop = my*10 -small.offsetHeight/2;
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

            function drawData() {


                maxLines = 0;
                var classes = 0;
                for (var i in data) {
                    var lines = data[i][1];
                    maxLines = Math.max(maxLines, lines[lines.length-1]+1);
                    classes++;
                }

                idx2cn = [];
                cn2idx = [];
                var c = 0;
                for (var className in data) {
                    idx2cn[c] = className;
                    cn2idx[className] = c;
                    c++;

                }


                var width = classes;
                var height = maxLines;

                if (width < 10) {
                    zoom = 10;
                } else if (width < 120) {
                    zoom = 3;
                }
                width = width * zoom;
                height = height * zoom;

                canvas.setAttribute("width", width);
                canvas.setAttribute("height", height);

                ctx.fillStyle = "rgb(0,0,0)";

                ctx.fillRect(0, 0, canvas.width, canvas.height);

                drawLines(zoom, maxLines);
                drawSmall();
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
                return [x, y];
            }

            function getClassAndLine(evt) {

                var cpos = getElemPos(scroll);

                var mx = evt.clientX - cpos[0] + scroll.scrollLeft;
                var my = evt.clientY - cpos[1] + scroll.scrollTop;


                var clsId = parseInt(mx / zoom);
                var cls = data[idx2cn[clsId]];
                var classLength = cls[1][cls[1].length-1];
                var wouldBeLength = parseInt(my / zoom);
                var lineId = Math.min(classLength, wouldBeLength);
                var id = idx2cn[clsId];
                var className = id.substr(id.indexOf("+")+1)
                var classLoaderId = id.substr(0, id.indexOf("+"))
                return {id: id, classLoaderId: classLoaderId, className: className, lineId: lineId};
            }


            function drawSmall() {
                var w = canvas.width/10;
                small.width = w;
                var h = canvas.height/10;
                small.height = h;

                smallCtx.drawImage(canvas, 0, 0, w, h)

                var cs = scroll;

                smallCtx.fillStyle = "rgba(255,255,255,0.2)";
                smallCtx.strokeStyle = "rgb(255,255,255)";
                smallCtx.rect(cs.scrollLeft/10, cs.scrollTop/10, cs.offsetWidth/10,cs.offsetHeight/10);
                smallCtx.fill();
                smallCtx.stroke();
            }

            function drawLines() {

                for (var id in data) {
                    drawLine(id, false);
                }
            }

            function drawLine(id, highlight) {

                var classData = data[id];
                var c = cn2idx[id];
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

        }


        return {
            link: link,
            scope: {
                revocPixels: "=",
                revocDataVersion: "=",
                onPixelHover: "&",
                onPixelClick: "&"

            }
        }
    }]);

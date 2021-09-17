// set the dimensions and margins of the graph
var margin = {top: 20, right: 10, bottom: 20, left: 40},
    width = 1200 - margin.left - margin.right,
    height = 300 - margin.top - margin.bottom;

// set the ranges
var x = d3.scaleBand()
          .range([0, width])
          .padding(0.1);
var y = d3.scaleLinear()
          .range([height, 0]);

// used to format the Y axis
var formatHMS = function(d) {
  var sec_num = parseInt(d, 10);
  var hours   = Math.floor(sec_num / 3600);
  var minutes = Math.floor((sec_num - (hours * 3600)) / 60);
  if (hours   < 10) {hours   = "0"+hours;}
  if (minutes < 10) {minutes = "0"+minutes;}
  return hours+':'+minutes;
}

// append the svg object to the body of the page
// append a 'group' element to 'svg'
// moves the 'group' element to the top left margin
d3.select("body").append("h1").text("Working time per day")
var perDayElem = d3.select("body").append("svg")
    .attr("id", "perDay")
    .attr("width", width + margin.left + margin.right)
    .attr("height", height + margin.top + margin.bottom)
  .append("g")
    .attr("transform",
          "translate(" + margin.left + "," + margin.top + ")");

perDay = $perDay;

// format the data
perDay.forEach(function(d) {
  d.time = +d.time;
});

// Scale the range of the data in the domains
x.domain(perDay.map(function(d) { return d.day; }));
y.domain([0, d3.max(perDay, function(d) { return d.time; })]);

// append the rectangles for the bar chart
perDayElem.selectAll(".bar")
    .data(perDay)
  .enter().append("rect")
    .attr("class", "bar")
    .attr("x", function(d) { return x(d.day); })
    .attr("width", x.bandwidth())
    .attr("y", function(d) { return y(d.time); })
    .attr("height", function(d) { return height - y(d.time); });

// add the x Axis
perDayElem.append("g")
    .attr("transform", "translate(0," + height + ")")
    .call(d3.axisBottom(x));

// y axis
perDayElem.append("g")
      .call(
        d3.axisLeft(y)
        .tickFormat(formatHMS)
    );
/** TIME PER PROJECT **/


d3.select("body").append("h1").text("Working time per project")
var perProjectElem = d3.select("body").append("svg")
    .attr("id", "perProject")
    .attr("width", width + margin.left + margin.right)
    .attr("height", height + margin.top + margin.bottom)
  .append("g")
    .attr("transform",
          "translate(" + margin.left + "," + margin.top + ")");

perProject = $perProject;

x.domain(perProject.map(function(d) { return d.project; }));
y.domain([0, d3.max(perProject, function(d) { return d.time; })]);


perProjectElem.selectAll(".bar")
    .data(perProject)
  .enter().append("rect")
    .attr("class", "bar")
    .attr("x", function(d) { return x(d.project); })
    .attr("width", x.bandwidth())
    .attr("y", function(d) { return y(d.time); })
    .attr("height", function(d) { return height - y(d.time); });

// add the x Axis
perProjectElem.append("g")
    .attr("transform", "translate(0," + height + ")")
    .call(d3.axisBottom(x));

perProjectElem.append("g")
    .call(
       d3.axisLeft(y)
       .tickFormat(formatHMS)
    );
/** TIME PER ISSUE **/


d3.select("body").append("h1").text("Working time per issues")
var perIssueElem = d3.select("body").append("svg")
    .attr("id", "perIssues")
    .attr("width", width + margin.left + margin.right)
    .attr("height", height + margin.top + margin.bottom)
  .append("g")
    .attr("transform",
          "translate(" + margin.left + "," + margin.top + ")");

perIssues = $perIssue;

x.domain(perIssues.map(function(d) { return d.issue; }));
y.domain([0, d3.max(perIssues, function(d) { return d.time; })]);

perIssueElem.selectAll(".bar")
    .data(perIssues)
  .enter().append("rect")
    .attr("class", "bar")
    .attr("x", function(d) { return x(d.issue); })
    .attr("width", x.bandwidth())
    .attr("y", function(d) { return y(d.time); })
    .attr("height", function(d) { return height - y(d.time); });

// add the x Axis
perIssueElem.append("g")
    .attr("transform", "translate(0," + height + ")")
    .call(d3.axisBottom(x));

perIssueElem.append("g")
    .call(
       d3.axisLeft(y)
       .tickFormat(formatHMS)
    );
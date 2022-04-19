window.requestAnimationFrame = window.requestAnimationFrame || window.mozRequestAnimationFrame ||
    window.webkitRequestAnimationFrame || window.msRequestAnimationFrame;

var clicksHeat = simpleheat('clicks').data(clicks).max(100), frame;
var movementsHeat = simpleheat('movements').data(movements).max(100), frame;

function draw() {
    clicksHeat.draw()
    movementsHeat.draw(0.01)
    frame = null;
}

draw();

let i = 1;
//set default
const r = document.querySelector(':root');
r.style.setProperty('--imageSource', 'url("/screenshots/' + appVersion + '/screenshot' + i + '.png")');

function changeBackground() {
    let imageCount = appVersion === "3.0" ? 6 : 4;
    if (i === imageCount) {
        i = 1
    } else {
        i++
    }
    r.style.setProperty('--imageSource', 'url("/screenshots/' + appVersion + '/screenshot' + i + '.png")');
}


import * as Cesium from 'cesium';

// Orbit maths works in a space-fixed frame; Cesium draws in an Earth-fixed frame.
// This matrix, computed for one moment in time, converts from the first to the second.
export function fixedFrame(tMs) {
  return Cesium.Transforms.computeTemeToPseudoFixedMatrix(
    Cesium.JulianDate.fromDate(new Date(tMs)),
  );
}

/** Converts a position in km (space-fixed) to a Cesium position in metres (Earth-fixed). */
export function toCartesian(matrix, vKm) {
  const v = new Cesium.Cartesian3(vKm[0] * 1000, vKm[1] * 1000, vKm[2] * 1000);
  return Cesium.Matrix3.multiplyByVector(matrix, v, v);
}

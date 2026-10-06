import SwiftUI
import UIKit

/// A quarter-ish arc that turns forever, animated by Core Animation in the
/// render server. A SwiftUI `.repeatForever` rotation is interpolated on the
/// main thread and commits a layer transaction every frame; a home list of
/// working bots did that once per arc, and spent a third of the main thread
/// on commits while nothing else changed. This view sets one infinite
/// `CABasicAnimation` and the app does no further work until it changes.
struct SpinningArc: UIViewRepresentable {
    var color: Color
    var track: Color = .clear
    var lineWidth: CGFloat = 1.5
    /// How much of the circle the arc covers (0...1).
    var length: CGFloat = 0.28
    /// Seconds per turn.
    var period: CFTimeInterval = 2.4
    /// Still, full ring when false (Reduce Motion).
    var turning = true

    func makeUIView(context: Context) -> ArcView { ArcView() }

    func updateUIView(_ view: ArcView, context: Context) {
        let traits = view.traitCollection
        view.configure(
            color: UIColor(color).resolvedColor(with: traits).cgColor,
            track: UIColor(track).resolvedColor(with: traits).cgColor,
            lineWidth: lineWidth,
            length: turning ? length : 1,
            period: period,
            turning: turning
        )
    }

    final class ArcView: UIView {
        private let trackLayer = CAShapeLayer()
        private let arcLayer = CAShapeLayer()
        private var turning = false
        private var period: CFTimeInterval = 2.4

        override init(frame: CGRect) {
            super.init(frame: frame)
            isUserInteractionEnabled = false
            backgroundColor = .clear
            for shape in [trackLayer, arcLayer] {
                shape.fillColor = nil
                shape.lineCap = .round
                layer.addSublayer(shape)
            }
        }

        required init?(coder: NSCoder) { fatalError("init(coder:) is not used") }

        func configure(color: CGColor, track: CGColor, lineWidth: CGFloat, length: CGFloat, period: CFTimeInterval, turning: Bool) {
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            trackLayer.strokeColor = track
            arcLayer.strokeColor = color
            trackLayer.lineWidth = lineWidth
            arcLayer.lineWidth = lineWidth
            arcLayer.strokeEnd = length
            CATransaction.commit()
            if turning != self.turning || period != self.period {
                self.turning = turning
                self.period = period
                applyAnimation()
            }
        }

        override func layoutSubviews() {
            super.layoutSubviews()
            let inset = arcLayer.lineWidth / 2
            let path = UIBezierPath(ovalIn: bounds.insetBy(dx: inset, dy: inset)).cgPath
            for shape in [trackLayer, arcLayer] {
                shape.frame = bounds
                shape.path = path
            }
            // Start at twelve o'clock, like the SwiftUI arcs it replaces.
            arcLayer.setAffineTransform(CGAffineTransform(rotationAngle: -.pi / 2))
        }

        /// A view moved off and back on screen loses its animations.
        override func didMoveToWindow() {
            super.didMoveToWindow()
            if window != nil { applyAnimation() }
        }

        private func applyAnimation() {
            arcLayer.removeAnimation(forKey: "turn")
            guard turning, window != nil else { return }
            let spin = CABasicAnimation(keyPath: "transform.rotation.z")
            spin.fromValue = -Double.pi / 2
            spin.toValue = Double.pi * 1.5
            spin.duration = period
            spin.repeatCount = .infinity
            spin.isRemovedOnCompletion = false
            arcLayer.add(spin, forKey: "turn")
        }
    }
}

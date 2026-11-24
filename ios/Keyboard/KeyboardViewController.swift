import UIKit

/// Sahi Bol keyboard: normal typing + a "Fix" bar that corrects the whole message.
final class KeyboardViewController: UIInputViewController {

    // Keycap palette: face colour + darker "lip" under it
    struct Cap { let face: UIColor; let lip: UIColor; let text: UIColor }
    private static func hex(_ v: UInt32) -> UIColor {
        UIColor(red: CGFloat((v >> 16) & 0xFF) / 255, green: CGFloat((v >> 8) & 0xFF) / 255, blue: CGFloat(v & 0xFF) / 255, alpha: 1)
    }
    private let letterCap = Cap(face: hex(0x3A3D40), lip: hex(0x1F2123), text: hex(0xF2F3F4))
    private let numberCap = Cap(face: hex(0xC9CDD1), lip: hex(0x8A9096), text: hex(0x141414))
    private let darkCap = Cap(face: hex(0x26282A), lip: hex(0x121314), text: hex(0xF2F3F4))
    private let orangeCap = Cap(face: hex(0xFF6A13), lip: hex(0xB04606), text: hex(0x111111))
    private let pinkCap = Cap(face: hex(0xFF45D2), lip: hex(0xA8238A), text: hex(0x111111))
    private let blueCap = Cap(face: hex(0x9BBCCB), lip: hex(0x5E7F8F), text: hex(0x111111))
    private let whiteCap = Cap(face: hex(0xEEF0F2), lip: hex(0xA3A9AF), text: hex(0x111111))
    private let navyCap = Cap(face: hex(0x1F3C91), lip: hex(0x10225A), text: .white)
    private let ink = hex(0x141414)
    private func bold(_ size: CGFloat) -> UIFont { .systemFont(ofSize: size, weight: .heavy, width: .condensed) }

    private let status = UILabel()
    private let resultBox = UIStackView()
    private let resultLabel = UILabel()
    private let reasonsLabel = UILabel()

    private var caps = false
    private var letterButtons: [UIButton] = []
    private var pendingFixed: String?
    private var originalLength = 0
    private var busy = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = Self.hex(0x0E0F10)

        let root = UIStackView()
        root.axis = .vertical
        root.spacing = 6
        root.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(root)
        NSLayoutConstraint.activate([
            root.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 4),
            root.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -4),
            root.topAnchor.constraint(equalTo: view.topAnchor, constant: 6),
            root.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -4),
        ])

        // Result card
        resultBox.axis = .vertical
        resultBox.spacing = 6
        resultBox.backgroundColor = Self.hex(0xEEF0F2)
        resultBox.layer.cornerRadius = 14
        resultBox.isLayoutMarginsRelativeArrangement = true
        resultBox.directionalLayoutMargins = .init(top: 10, leading: 12, bottom: 10, trailing: 12)
        resultLabel.font = bold(17)
        resultLabel.textColor = ink
        resultLabel.numberOfLines = 0
        reasonsLabel.font = .systemFont(ofSize: 13)
        reasonsLabel.textColor = .darkGray
        reasonsLabel.numberOfLines = 0
        let resultButtons = UIStackView(arrangedSubviews: [
            pill("KEEP MINE", cap: whiteCap) { [weak self] in self?.hideResult() },
            pill("USE THIS", cap: orangeCap) { [weak self] in self?.applyFix() },
        ])
        resultButtons.spacing = 6
        resultButtons.distribution = .fillEqually
        [resultLabel, reasonsLabel, resultButtons].forEach(resultBox.addArrangedSubview)
        resultBox.isHidden = true
        root.addArrangedSubview(resultBox)

        // Fix bar
        let bar = UIStackView(arrangedSubviews: [
            pill("✓ FIX", cap: orangeCap) { [weak self] in self?.fix("normal") },
            pill("POLITE", cap: pinkCap) { [weak self] in self?.fix("polite") },
            pill("SHORT", cap: blueCap) { [weak self] in self?.fix("short") },
            pill("हिंदी", cap: whiteCap) { [weak self] in self?.fix("hindi") },
        ])
        bar.spacing = 6
        bar.distribution = .fillProportionally
        root.addArrangedSubview(bar)

        status.font = .systemFont(ofSize: 12)
        status.textColor = Self.hex(0xB8BCC0)
        status.numberOfLines = 2
        status.isHidden = true
        root.addArrangedSubview(status)

        // Keys
        root.addArrangedSubview(row("1234567890".map { c in key(String(c), cap: numberCap) { [weak self] in self?.type(String(c)) } }))
        root.addArrangedSubview(row("qwertyuiop".map(letter)))
        root.addArrangedSubview(row("asdfghjkl".map(letter), inset: 16))
        root.addArrangedSubview(row(
            [key("⇧", width: 1.4, cap: darkCap) { [weak self] in self?.toggleCaps() }] +
            "zxcvbnm".map(letter) +
            [key("⌫", width: 1.4, cap: darkCap) { [weak self] in self?.textDocumentProxy.deleteBackward() }]
        ))
        let globe = key("🌐", width: 1.2, cap: darkCap) {}
        globe.addTarget(self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents)
        root.addArrangedSubview(row([
            globe,
            key(",", cap: darkCap) { [weak self] in self?.type(",") },
            key("SAHI BOL", width: 4.5, cap: navyCap) { [weak self] in self?.type(" ") },
            key(".", cap: darkCap) { [weak self] in self?.type(".") },
            key("↵", width: 1.6, cap: orangeCap) { [weak self] in self?.textDocumentProxy.insertText("\n") },
        ]))
    }

    // MARK: Fixing

    private func fix(_ tone: String) {
        guard !busy else { return }
        guard hasFullAccess else {
            return showStatus("Turn on Allow Full Access: Settings › Keyboards › Sahi Bol")
        }
        let before = textDocumentProxy.documentContextBeforeInput ?? ""
        let after = textDocumentProxy.documentContextAfterInput ?? ""
        let text = before + after
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return showStatus("Type a message first")
        }
        busy = true
        showStatus("Fixing…")
        Api.fix(text: text, tone: tone) { [weak self] result in
            DispatchQueue.main.async {
                guard let self else { return }
                self.busy = false
                switch result {
                case .success(let r):
                    self.status.isHidden = true
                    self.pendingFixed = r.fixed
                    self.originalLength = text.count
                    self.resultLabel.text = r.fixed
                    let reasons = (r.reasons ?? []).map { "• \($0.from ?? "") → \($0.to ?? ""): \($0.why ?? "")" }
                    self.reasonsLabel.text = reasons.isEmpty ? "Already good 👍" : reasons.joined(separator: "\n")
                    self.resultBox.isHidden = false
                case .failure:
                    self.showStatus("No internet or server busy. Try again.")
                }
            }
        }
    }

    private func applyFix() {
        guard let fixed = pendingFixed else { return }
        let after = textDocumentProxy.documentContextAfterInput ?? ""
        if !after.isEmpty { textDocumentProxy.adjustTextPosition(byCharacterOffset: after.count) }
        for _ in 0..<originalLength { textDocumentProxy.deleteBackward() }
        textDocumentProxy.insertText(fixed)
        hideResult()
    }

    private func hideResult() {
        pendingFixed = nil
        resultBox.isHidden = true
    }

    private func showStatus(_ msg: String) {
        status.text = msg
        status.isHidden = false
    }

    // MARK: Typing

    private func type(_ s: String) {
        textDocumentProxy.insertText(s)
        if caps { toggleCaps() }
    }

    private func toggleCaps() {
        caps.toggle()
        for b in letterButtons {
            let t = b.title(for: .normal) ?? ""
            b.setTitle(caps ? t.uppercased() : t.lowercased(), for: .normal)
        }
    }

    // MARK: Views

    private func letter(_ c: Character) -> UIButton {
        let b = key(String(c)) {}
        b.addAction(UIAction { [weak self, weak b] _ in
            self?.type(b?.title(for: .normal) ?? "")
        }, for: .touchUpInside)
        letterButtons.append(b)
        return b
    }

    private func key(_ label: String, width: CGFloat = 1, cap: Cap? = nil, action: @escaping () -> Void) -> UIButton {
        let cap = cap ?? letterCap
        let b = UIButton(type: .custom)
        b.setTitle(label, for: .normal)
        b.titleLabel?.font = label == "SAHI BOL" ? bold(13) : bold(20)
        styleCap(b, cap, radius: 7)
        b.addAction(UIAction { _ in action() }, for: .touchUpInside)
        b.heightAnchor.constraint(equalToConstant: 44).isActive = true
        b.tag = Int(width * 10) // used by row() for widths
        return b
    }

    private func pill(_ label: String, cap: Cap, action: @escaping () -> Void) -> UIButton {
        let b = UIButton(type: .custom)
        b.setTitle(label, for: .normal)
        b.titleLabel?.font = bold(15)
        styleCap(b, cap, radius: 10)
        b.contentEdgeInsets = UIEdgeInsets(top: 0, left: 14, bottom: 0, right: 14)
        b.heightAnchor.constraint(equalToConstant: 42).isActive = true
        b.addAction(UIAction { _ in action() }, for: .touchUpInside)
        return b
    }

    /// Chunky keycap: solid darker lip under the face; the face sinks when pressed.
    private func styleCap(_ b: UIButton, _ cap: Cap, radius: CGFloat) {
        b.setTitleColor(cap.text, for: .normal)
        b.backgroundColor = cap.face
        b.layer.cornerRadius = radius
        b.layer.shadowColor = cap.lip.cgColor
        b.layer.shadowOpacity = 1
        b.layer.shadowRadius = 0
        b.layer.shadowOffset = CGSize(width: 0, height: 4)
        b.addAction(UIAction { [weak b] _ in
            b?.transform = CGAffineTransform(translationX: 0, y: 3)
            b?.layer.shadowOffset = CGSize(width: 0, height: 1)
        }, for: .touchDown)
        b.addAction(UIAction { [weak b] _ in
            UIView.animate(withDuration: 0.08) { b?.transform = .identity }
            b?.layer.shadowOffset = CGSize(width: 0, height: 4)
        }, for: [.touchUpInside, .touchUpOutside, .touchCancel])
    }

    private func row(_ keys: [UIButton], inset: CGFloat = 0) -> UIView {
        let stack = UIStackView()
        stack.axis = .horizontal
        stack.spacing = 5
        stack.isLayoutMarginsRelativeArrangement = true
        stack.directionalLayoutMargins = .init(top: 3, leading: inset, bottom: 3, trailing: inset)
        guard let first = keys.first else { return stack }
        for k in keys {
            stack.addArrangedSubview(k)
            if k !== first {
                let ratio = CGFloat(k.tag) / CGFloat(first.tag)
                k.widthAnchor.constraint(equalTo: first.widthAnchor, multiplier: ratio).isActive = true
            }
        }
        return stack
    }
}

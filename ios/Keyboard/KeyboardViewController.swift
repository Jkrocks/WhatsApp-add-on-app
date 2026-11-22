import UIKit

/// Sahi Bol keyboard: normal typing + a "Fix" bar that corrects the whole message.
final class KeyboardViewController: UIInputViewController {

    private let green = UIColor(red: 11/255, green: 122/255, blue: 83/255, alpha: 1)
    private let ink = UIColor(red: 19/255, green: 32/255, blue: 26/255, alpha: 1)
    private let keyGrey = UIColor(red: 185/255, green: 194/255, blue: 189/255, alpha: 1)

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
        view.backgroundColor = UIColor(red: 216/255, green: 222/255, blue: 219/255, alpha: 1)

        let root = UIStackView()
        root.axis = .vertical
        root.spacing = 4
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
        resultBox.backgroundColor = .white
        resultBox.layer.cornerRadius = 14
        resultBox.isLayoutMarginsRelativeArrangement = true
        resultBox.directionalLayoutMargins = .init(top: 10, leading: 12, bottom: 10, trailing: 12)
        resultLabel.font = .systemFont(ofSize: 16, weight: .semibold)
        resultLabel.textColor = ink
        resultLabel.numberOfLines = 0
        reasonsLabel.font = .systemFont(ofSize: 13)
        reasonsLabel.textColor = .darkGray
        reasonsLabel.numberOfLines = 0
        let resultButtons = UIStackView(arrangedSubviews: [
            pill("Keep mine", primary: false) { [weak self] in self?.hideResult() },
            pill("Use this", primary: true) { [weak self] in self?.applyFix() },
        ])
        resultButtons.spacing = 6
        resultButtons.distribution = .fillEqually
        [resultLabel, reasonsLabel, resultButtons].forEach(resultBox.addArrangedSubview)
        resultBox.isHidden = true
        root.addArrangedSubview(resultBox)

        // Fix bar
        let bar = UIStackView(arrangedSubviews: [
            pill("✓ Fix", primary: true) { [weak self] in self?.fix("normal") },
            pill("Polite", primary: false) { [weak self] in self?.fix("polite") },
            pill("Short", primary: false) { [weak self] in self?.fix("short") },
            pill("हिंदी", primary: false) { [weak self] in self?.fix("hindi") },
        ])
        bar.spacing = 6
        bar.distribution = .fillProportionally
        root.addArrangedSubview(bar)

        status.font = .systemFont(ofSize: 12)
        status.textColor = .darkGray
        status.numberOfLines = 2
        status.isHidden = true
        root.addArrangedSubview(status)

        // Keys
        root.addArrangedSubview(row("1234567890".map { c in key(String(c)) { [weak self] in self?.type(String(c)) } }))
        root.addArrangedSubview(row("qwertyuiop".map(letter)))
        root.addArrangedSubview(row("asdfghjkl".map(letter), inset: 16))
        root.addArrangedSubview(row(
            [key("⇧", width: 1.4, bg: keyGrey) { [weak self] in self?.toggleCaps() }] +
            "zxcvbnm".map(letter) +
            [key("⌫", width: 1.4, bg: keyGrey) { [weak self] in self?.textDocumentProxy.deleteBackward() }]
        ))
        let globe = key("🌐", width: 1.2, bg: keyGrey) {}
        globe.addTarget(self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents)
        root.addArrangedSubview(row([
            globe,
            key(",") { [weak self] in self?.type(",") },
            key("space", width: 4.5) { [weak self] in self?.type(" ") },
            key(".") { [weak self] in self?.type(".") },
            key("↵", width: 1.6, bg: keyGrey) { [weak self] in self?.textDocumentProxy.insertText("\n") },
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

    private func key(_ label: String, width: CGFloat = 1, bg: UIColor = .white, action: @escaping () -> Void) -> UIButton {
        let b = UIButton(type: .system)
        b.setTitle(label, for: .normal)
        b.setTitleColor(ink, for: .normal)
        b.titleLabel?.font = .systemFont(ofSize: label.count > 1 ? 15 : 20)
        b.backgroundColor = bg
        b.layer.cornerRadius = 7
        b.layer.shadowColor = UIColor.black.cgColor
        b.layer.shadowOpacity = 0.2
        b.layer.shadowOffset = CGSize(width: 0, height: 1)
        b.layer.shadowRadius = 0
        b.addAction(UIAction { _ in action() }, for: .touchUpInside)
        b.heightAnchor.constraint(equalToConstant: 42).isActive = true
        b.tag = Int(width * 10) // used by row() for widths
        return b
    }

    private func pill(_ label: String, primary: Bool, action: @escaping () -> Void) -> UIButton {
        let b = UIButton(type: .system)
        b.setTitle(label, for: .normal)
        b.setTitleColor(primary ? .white : ink, for: .normal)
        b.titleLabel?.font = .systemFont(ofSize: 14, weight: primary ? .semibold : .regular)
        b.backgroundColor = primary ? green : .white
        b.layer.cornerRadius = 20
        b.contentEdgeInsets = UIEdgeInsets(top: 0, left: 14, bottom: 0, right: 14)
        b.heightAnchor.constraint(equalToConstant: 40).isActive = true
        b.addAction(UIAction { _ in action() }, for: .touchUpInside)
        return b
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

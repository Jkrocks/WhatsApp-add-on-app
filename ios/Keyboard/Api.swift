import Foundation

struct FixResult: Decodable {
    struct Reason: Decodable {
        let from: String?
        let to: String?
        let why: String?
    }
    let fixed: String
    let reasons: [Reason]?
}

struct ApiError: Error {}

enum Api {
    static func fix(text: String, tone: String, done: @escaping (Result<FixResult, Error>) -> Void) {
        let info = Bundle.main.infoDictionary ?? [:]
        let base = (info["SahiBolServerURL"] as? String ?? "").trimmingCharacters(in: CharacterSet(charactersIn: "/ "))
        let key = info["SahiBolAppKey"] as? String ?? ""
        guard let url = URL(string: base + "/fix") else { return done(.failure(ApiError())) }

        var req = URLRequest(url: url, timeoutInterval: 40)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue(key, forHTTPHeaderField: "x-app-key")
        req.httpBody = try? JSONSerialization.data(withJSONObject: ["text": text, "tone": tone])

        URLSession.shared.dataTask(with: req) { data, response, error in
            if let error { return done(.failure(error)) }
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode),
                  let data, let result = try? JSONDecoder().decode(FixResult.self, from: data)
            else { return done(.failure(ApiError())) }
            done(.success(result))
        }.resume()
    }
}

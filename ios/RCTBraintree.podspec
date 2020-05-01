require 'json'

package = JSON.parse(File.read(File.join(__dir__, '../package.json')))

Pod::Spec.new do |s|
  s.name = 'RCTBraintree'
  s.version = package['version']
  s.summary = package['description']
  s.description = package['description']
  s.homepage = 'https://github.com/wkoutre/react-native-braintree-xplat.git'
  s.license = package['license']
  s.author = { 'Nick Koutrelakos' => 'nick@stadiumgoods.com' }
  s.platform = :ios, '10.0'
  s.source = { :git => 'https://github.com/wkoutre/react-native-braintree-xplat.git', :tag => 'master' }
  s.source_files = 'RCTBraintree/**/*.{h,m}'
  s.requires_arc = true

  s.ios.deployment_target = '10.0'
  s.tvos.deployment_target = '10.0'

  s.dependency 'Braintree', '4.33.0'
  s.dependency 'BraintreeDropIn', '4.33.0'
  s.dependency 'Braintree/PayPal', '4.33.0'
  s.dependency 'Braintree/Apple-Pay', '4.33.0'
  s.dependency 'Braintree/3D-Secure', '4.33.0'
  s.dependency 'Braintree/DataCollector', '4.33.0'
  s.dependency 'React'
end

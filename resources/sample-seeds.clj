{:triggers
 {:lein {:git-url '%s', :branch '%s', :type 'build', :builder 'lein'}
  :docker  {:volumes 'm2', :type 'docker', :image '%s', :args '%a'}}

 :schedules
 {:report  {:schedule "*/1", :type 'docker', :image 'report-1.0'}}}

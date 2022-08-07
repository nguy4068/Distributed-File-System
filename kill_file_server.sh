ssh -t -n -f nguy4068@kh4250-11.cselabs.umn.edu  'lsof -t -i:4000 | xargs -r kill'
